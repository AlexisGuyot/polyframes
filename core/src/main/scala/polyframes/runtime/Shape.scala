package polyframes.runtime

import org.apache.spark.sql.Column
import org.apache.spark.sql.functions.struct
import shapeless.labelled.FieldType
import shapeless.{::, HList, HNil, Witness}

import scala.annotation.implicitNotFound

/**
 * The list of columns a schema type designates, in the order the schema
 * gives them.
 *
 * This is what keeps the DataFrame and its schema type in step. Every
 * operator hands its result to the factory of `Data`, which selects the
 * columns this type class produces, so the column order of a DataFrame is
 * always the attribute order of its schema type. The row decoder can then
 * decode by position, and no operator has to worry about where Spark chose
 * to put the column it added or renamed.
 *
 * It also means projection and removal need no code of their own: selecting
 * the columns of the output schema performs both.
 *
 * `get` resolves an attribute name at the current level of nesting. At the
 * root it reads a column of the DataFrame; inside a nested schema it reads
 * a field of the enclosing column.
 *
 * Collections of nested schemas are not handled: rebuilding those would
 * require a higher-order function over arrays, which no pipeline of
 * section 5 needs. The type system accepts them; this layer does not
 * materialise them.
 */
@implicitNotFound("PolyFrames: cannot lay out the columns of the schema\n    ${S}")
trait Shape[S <: HList] { def columns(get: String => Column): List[Column] }

object Shape extends LowPriorityShape {
  def apply[S <: HList](implicit shape: Shape[S]): Shape[S] = shape

  implicit val empty: Shape[HNil] = new Shape[HNil] {
    def columns(get: String => Column): List[Column] = Nil
  }

  /** A nested schema is rebuilt as a struct of its own columns. */
  implicit def nested[K <: String, Sub <: HList, N <: HList](
      implicit 
      key: Witness.Aux[K],
      inner: Shape[Sub],
      tail: Shape[N]
  ): Shape[FieldType[K, Sub] :: N] = new Shape[FieldType[K, Sub] :: N] {
    def columns(get: String => Column): List[Column] = {
      val here = get(key.value)
      struct(inner.columns(name => here.getField(name)): _*)
        .as(key.value) :: tail.columns(get)
    }
  }
}

trait LowPriorityShape {

  /** Any other domain is read as is. */
  implicit def atomic[K <: String, T, N <: HList](
      implicit key: Witness.Aux[K],
      tail: Shape[N]
  ): Shape[FieldType[K, T] :: N] = new Shape[FieldType[K, T] :: N] {
    def columns(get: String => Column): List[Column] =
      get(key.value).as(key.value) :: tail.columns(get)
  }
}
