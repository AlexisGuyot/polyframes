package polyframes.bench

import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
import polyframes.runtime.Data
import polyframes.types.Relation
import shapeless.HList

/**
 * Writing the result of a pipeline to a CSV file.
 *
 * A sink of this kind accepts relational data and nothing else: a comma
 * separated file has no way of carrying a nested or a multi-valued
 * attribute. It is therefore the natural place at which the model of the
 * data stops being a matter of taste, and it is what the pipelines of
 * section 5 end on.
 *
 * The three versions below differ in when they say so. The typed one
 * requires its operand to be in the relational model, and refuses anything
 * else while the program is being compiled. The other two accept whatever
 * they are given, and leave Spark to discover the problem when the file is
 * actually written, which may be long after, and far from, the step that
 * caused it.
 */
object Sink {

  /** Only relational data can be written. Checked by the compiler. */
  def typed[S <: HList](d: Data[Relation, S], path: String): Unit =
    d.df.write.mode("overwrite").csv(path)

  /** Anything is accepted. Checked, if at all, at run time. */
  def untyped(df: DataFrame, path: String): Unit =
    df.write.mode("overwrite").csv(path)

  /** Likewise: a Dataset does not record the model of its data. */
  def ofDataset[T](ds: Dataset[T], path: String): Unit =
    ds.write.mode("overwrite").csv(path)

  /** A directory of its own for each write. See Workspace.out. */
  def scratch(spark: SparkSession, name: String): String = Workspace.out(name)
}
