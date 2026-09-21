package polyframes.runtime

import org.apache.spark.sql.types._
import shapeless.labelled.FieldType
import shapeless.{::, HList, HNil, Witness}

import scala.annotation.implicitNotFound

/**
 * Spark counterpart of an attribute domain.
 *
 * Needed when a new attribute is created: Spark must be told the type of
 * the column being added. The instances mirror those of `RelType` and of
 * `FromCell`, for the same reason as before, so that a domain the type
 * system admits is a domain Spark can be told about.
 *
 * This replaces the `TypeTag` context bound a Scala UDF would otherwise
 * require. Scala reflection is thereby kept out of the library altogether.
 */
@implicitNotFound("PolyFrames: ${T} has no counterpart among Spark data types.")
trait SparkType[T] { def dataType: DataType }

object SparkType {
  def apply[T](implicit st: SparkType[T]): SparkType[T] = st

  private def of[T](dt: DataType): SparkType[T] = new SparkType[T] {
    val dataType: DataType = dt
  }

  implicit val sparkString: SparkType[String]   = of(StringType)
  implicit val sparkInt: SparkType[Int]         = of(IntegerType)
  implicit val sparkLong: SparkType[Long]       = of(LongType)
  implicit val sparkDouble: SparkType[Double]   = of(DoubleType)
  implicit val sparkBoolean: SparkType[Boolean] = of(BooleanType)

  /** A collection, mirroring the rule that admits multi-valued domains. */
  implicit def sparkList[T](implicit element: SparkType[T]): SparkType[List[T]] =
    of(ArrayType(element.dataType))

  /** A nested schema, mirroring the rule that admits nested ones. */
  implicit def sparkNested[S <: HList](implicit inner: SparkSchema[S]): SparkType[S] =
    of(inner.structType)
}

/**
 * The SparkSQL schema a schema type describes.
 *
 * Used when reading a file: handing the reader an explicit schema spares
 * it an inference pass over the data, and confines what it reads to the
 * attributes the pipeline declares. On a dump whose records carry
 * thousands of fields, that is the difference between reading a slice and
 * reading everything.
 */
@implicitNotFound("PolyFrames: no SparkSQL schema for\n    ${S}")
trait SparkSchema[S <: HList] {
  def fields: List[StructField]
  final def structType: StructType = StructType(fields)
}

object SparkSchema {
  def apply[S <: HList](implicit s: SparkSchema[S]): SparkSchema[S] = s

  implicit val empty: SparkSchema[HNil] = new SparkSchema[HNil] {
    val fields: List[StructField] = Nil
  }

  implicit def attribute[K <: String, T, N <: HList](
      implicit 
      key: Witness.Aux[K],
      domain: SparkType[T],
      tail: SparkSchema[N]
  ): SparkSchema[FieldType[K, T] :: N] = new SparkSchema[FieldType[K, T] :: N] {
    val fields: List[StructField] =
      StructField(key.value, domain.dataType, nullable = true) :: tail.fields
  }
}
