package polyframes.runtime

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions.col
import polyframes.types.{Model, Valid}
import shapeless.HList

/**
 * The typed layer over a SparkSQL DataFrame. Encodes rule 9 of the article.
 *
 * This is the only inhabited type of the system: every other trait is
 * populated by proof witnesses alone. A value of `Data[M, S]` carries the
 * data, in the form of the DataFrame it wraps, and carries in its type the
 * model and the schema that data is known to have.
 *
 * The constructor is private, so the factory below is the only way to
 * obtain such a value, and the factory requires evidence of conformance.
 * No value of type `Data[M, S]` can therefore exist whose schema has not
 * been checked against its model.
 */
final class Data[M <: Model, S <: HList] private (
    private[polyframes] val df: DataFrame
)

object Data {

  /**
   * Rule 9. The two subtyping premises are the bounds on the type
   * parameters; the conformance premise is the `valid` parameter. The
   * `shape` parameter has no counterpart in the rule: it belongs to the
   * layer binding the type system to Spark, and restores the invariant
   * above.
   */
  def apply[M <: Model, S <: HList](df: DataFrame)(
      implicit 
      valid: Valid[M, S],
      shape: Shape[S]
  ): Data[M, S] =
    new Data[M, S](df.select(shape.columns(name => col(s"`$name`")): _*))
}
