package polyframes.runtime

import org.apache.spark.sql.functions.col
import org.apache.spark.sql.{Column, DataFrame}
import shapeless.{::, HList, HNil, Witness}

import scala.annotation.implicitNotFound

/** The attribute names a path is made of, at run time. */
trait Names[P <: HList] { def apply(): List[String] }

object Names {
  implicit val empty: Names[HNil] = () => Nil

  implicit def cons[K <: String, N <: HList](
      implicit key: Witness.Aux[K],
      tail: Names[N]
  ): Names[K :: N] = () => key.value :: tail()
}

/**
 * The column a path designates, and the three ways of acting on it.
 *
 * A path of length one designates a column of the DataFrame; a longer one
 * designates a field nested inside it, reached with the `withField` and
 * `dropFields` operations SparkSQL provides on struct columns.
 *
 * None of these operations is required to leave the column in the position
 * the schema type gives it: the factory of `Data` re-selects the columns
 * in schema order afterwards, so position is not this type's concern.
 */
@implicitNotFound("PolyFrames: ${P} is not a well-formed path.")
trait Target[P <: HList] {
  def path: List[String]

  private def quoted(name: String): String = s"`$name`"

  final def root: String = path.head
  final def belowRoot: String = path.tail.map(quoted).mkString(".")
  final def dotted: String = path.map(quoted).mkString(".")
  final def isRoot: Boolean = path.lengthCompare(1) == 0

  /** Adds the column, or replaces it if the path already designates one. */
  final def insert(df: DataFrame, value: Column): DataFrame =
    if (isRoot) df.withColumn(root, value)
    else df.withColumn(root, col(quoted(root)).withField(belowRoot, value))

  final def remove(df: DataFrame): DataFrame =
    if (isRoot) df.drop(root)
    else df.withColumn(root, col(quoted(root)).dropFields(belowRoot))

  final def renameTo(df: DataFrame, newName: String): DataFrame =
    if (isRoot) df.withColumnRenamed(root, newName)
    else {
      val moved = (path.tail.init :+ newName).map(quoted).mkString(".")
      df.withColumn(
        root,
        col(quoted(root)).withField(moved, col(dotted)).dropFields(belowRoot)
      )
    }
}

object Target {
  implicit def fromNames[P <: HList](implicit names: Names[P]): Target[P] =
    new Target[P] { val path: List[String] = names() }
}
