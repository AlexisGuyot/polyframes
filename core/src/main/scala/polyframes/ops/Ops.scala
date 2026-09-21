package polyframes.ops

import org.apache.spark.sql.api.java.UDF1
import org.apache.spark.sql.functions.{col, expr}
import org.apache.spark.sql.{Column, Row}
import polyframes.infer._
import polyframes.runtime._
import polyframes.types.{Model, Valid}
import shapeless.{HList, Witness}

import java.util.concurrent.atomic.AtomicLong

/**
 * The operators of the article.
 *
 * Each is introduced in two stages. The model of the output and the paths
 * an operator acts on are types that no argument determines, so they have
 * to be supplied by hand; the schemas, the input model and the inferred
 * output schema are determined by the arguments and must not be. Scala
 * offers no way of supplying some type parameters and inferring the others
 * in a single application, so each operator is a method returning a small
 * object whose `apply` carries the rest.
 *
 * The result reads exactly as the rules do:
 *
 *   add[Document, "identity" :: "adult" :: HNil](d, f)
 *   project[Relation, LProj](d)
 *
 * with the paths and the announced model at the type level, and the data
 * at the term level.
 *
 * Every operator ends by handing its DataFrame to the factory of `Data`,
 * which checks the inferred schema against the announced model and lays
 * the columns out in schema order. Projection and removal need nothing
 * else: selecting the columns of the output schema performs both.
 */
object Ops {

  private val udfCounter = new AtomicLong(0L)

  /** Rule 45. */
  def project[MO <: Model, L <: HList]: ProjectOp[MO, L] = new ProjectOp[MO, L]

  /** Rule 46. */
  def add[MO <: Model, P <: HList]: AddOp[MO, P] = new AddOp[MO, P]

  /** Appendix C, by analogy with rule 46. */
  def update[MO <: Model, P <: HList]: UpdateOp[MO, P] = new UpdateOp[MO, P]

  /** Appendix C, by analogy with rule 45. */
  def drop[MO <: Model, P <: HList]: DropOp[MO, P] = new DropOp[MO, P]

  /** Appendix C. */
  def rename[MO <: Model, P <: HList, KNew <: String]: RenameOp[MO, P, KNew] =
    new RenameOp[MO, P, KNew]

  /** Appendix C. The only binary operator. */
  def join[MO <: Model, PL <: HList, PR <: HList]: JoinOp[MO, PL, PR] =
    new JoinOp[MO, PL, PR]

  // -------------------------------------------------------------------

  final class ProjectOp[MO <: Model, L <: HList] {
    def apply[M <: Model, S <: HList, NS <: HList](d: Data[M, S])(
        implicit 
        reduce: Project.Aux[S, L, NS],
        valid: Valid[MO, NS],
        shape: Shape[NS]
    ): Data[MO, NS] = Data[MO, NS](d.df)
  }

  final class DropOp[MO <: Model, P <: HList] {
    def apply[M <: Model, S <: HList, NS <: HList](d: Data[M, S])(
        implicit 
        reduce: Drop.Aux[S, P, NS],
        valid: Valid[MO, NS],
        shape: Shape[NS]
    ): Data[MO, NS] = Data[MO, NS](d.df)
  }

  final class AddOp[MO <: Model, P <: HList] {
    // The operand and the function sit in two parameter lists, not one.
    // Scala infers a list as a whole, so with both in the same list the
    // schema `S` would not yet be fixed when the function is typed, and
    // its parameter would have to be annotated at every call site.
    def apply[M <: Model, S <: HList](d: Data[M, S]) = new Applied[M, S](d)

    final class Applied[M <: Model, S <: HList](d: Data[M, S]) {
      def apply[T, NS <: HList](f: S => T)(
        implicit 
        reduce: Add.Aux[S, P, T, NS],
        valid: Valid[MO, NS],
        shape: Shape[NS],
        decode: FromRow[S],
        target: Target[P],
        domain: SparkType[T]
      ): Data[MO, NS] =
        Data[MO, NS](target.insert(d.df, record(d, f, domain)))
    }
  }

  final class UpdateOp[MO <: Model, P <: HList] {
    def apply[M <: Model, S <: HList](d: Data[M, S]) = new Applied[M, S](d)

    final class Applied[M <: Model, S <: HList](d: Data[M, S]) {
      def apply[T, NS <: HList](f: S => T)(
        implicit 
        reduce: Update.Aux[S, P, T, NS],
        valid: Valid[MO, NS],
        shape: Shape[NS],
        decode: FromRow[S],
        target: Target[P],
        domain: SparkType[T]
      ): Data[MO, NS] =
        Data[MO, NS](target.insert(d.df, record(d, f, domain)))
    }
  }

  final class RenameOp[MO <: Model, P <: HList, KNew <: String] {
    def apply[M <: Model, S <: HList, NS <: HList](d: Data[M, S])(
        implicit 
        reduce: Rename.Aux[S, P, KNew, NS],
        valid: Valid[MO, NS],
        shape: Shape[NS],
        target: Target[P],
        name: Witness.Aux[KNew]
    ): Data[MO, NS] =
      Data[MO, NS](target.renameTo(d.df, name.value))
  }

  /**
   * Inner join on one attribute of each operand.
   *
   * The two join attributes must have the same domain, which the `=:=`
   * premise requires. The two schemas must also disagree on no attribute
   * name, since `Merge` reconciles homonymous attributes only when both
   * are nested schemas: joining two schemas that share a plain attribute
   * name is rejected at compile time rather than resolved by an arbitrary
   * choice between the two.
   */
  final class JoinOp[MO <: Model, PL <: HList, PR <: HList] {
    def apply[
        ML <: Model, MR <: Model, SL <: HList, SR <: HList,
        TL, TR, NS <: HList
    ](left: Data[ML, SL], right: Data[MR, SR])(
        implicit 
        keyLeft: Lookup.Aux[SL, PL, TL],
        keyRight: Lookup.Aux[SR, PR, TR],
        sameDomain: TL =:= TR,
        merge: Merge.Aux[SL, SR, NS],
        valid: Valid[MO, NS],
        shape: Shape[NS],
        targetLeft: Target[PL],
        targetRight: Target[PR]
    ): Data[MO, NS] =
      Data[MO, NS](
        left.df.join(
          right.df,
          col(targetLeft.dotted) === col(targetRight.dotted),
          "inner"
        )
      )
  }

  // -------------------------------------------------------------------

  /**
   * Turns a function over records into a column expression.
   *
   * The function is registered as a user-defined function taking the whole
   * record, then invoked on a struct of every column of the DataFrame.
   * Since the columns of a DataFrame are always in the order of its schema
   * type, that struct is a term of the schema type, which the decoder
   * rebuilds. This is what gives `f` the type `S => T` rule 46 demands.
   *
   * The registered function is shipped to the executors, so it closes over
   * two things that have to survive serialisation: `f`, which does because
   * every Scala function does, and the decoder, which is serialisable for
   * that reason alone. A function capturing something that is not will be
   * rejected by Spark at run time, as any Spark job would be.
   */
  private def record[M <: Model, S <: HList, T](
      d: Data[M, S],
      f: S => T,
      domain: SparkType[T]
  )(implicit decode: FromRow[S]): Column = {
    val name = "polyframes_udf_" + udfCounter.incrementAndGet()
    d.df.sparkSession.udf.register(
      name,
      new UDF1[Row, T] { def call(row: Row): T = f(decode(row, 0)) },
      domain.dataType
    )
    expr(s"$name(struct(*))")
  }
}
