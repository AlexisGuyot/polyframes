package polyframes.runtime

import org.apache.spark.sql.Row
import shapeless.labelled.{FieldType, field}
import shapeless.{::, HList, HNil}

import scala.annotation.implicitNotFound

/**
 * Decoder for a single attribute domain.
 *
 * The instances below mirror, one for one, the rules that define which
 * domains the document model admits: atomic values, collections whose
 * element domain is itself admissible, and nested schemas. 
 * A domain is decodable here exactly when the type system
 * accepts it there, so the decoder cannot be asked for a domain the system
 * would have rejected.
 *
 * A missing value decodes to the zero of its domain, and a missing
 * collection to the empty list. The type system has no notion of an
 * optional attribute (yet): an attribute is declared with a domain, and every
 * record is taken to carry a value of it. That is a simplification, and
 * the choice made here is to state it in one place rather than to let it
 * follow from how the JVM happens to unbox a null. Distinguishing an
 * absent value from a zero would call for optional attributes, which the
 * type system does not have.
 */
@implicitNotFound("PolyFrames: no way to decode a value of domain ${T}.")
trait FromCell[T] extends Serializable { def apply(value: Any): T }

object FromCell {

  private def cast[T]: FromCell[T] = new FromCell[T] {
    def apply(value: Any): T = value.asInstanceOf[T]
  }

  private def orElse[T](default: T): FromCell[T] = new FromCell[T] {
    def apply(value: Any): T =
      if (value == null) default else value.asInstanceOf[T]
  }

  implicit val cellString: FromCell[String]   = cast
  implicit val cellInt: FromCell[Int]         = orElse(0)
  implicit val cellLong: FromCell[Long]       = orElse(0L)
  implicit val cellDouble: FromCell[Double]   = orElse(0.0)
  implicit val cellBoolean: FromCell[Boolean] = orElse(false)

  /** A collection, decoded element by element. */
  implicit def cellList[T](implicit element: FromCell[T]): FromCell[List[T]] =
    new FromCell[List[T]] {
      def apply(value: Any): List[T] = value match {
        case null                      => Nil
        case s: scala.collection.Seq[_] => s.iterator.map(element.apply).toList
        case a: Array[_]               => a.iterator.map(element.apply).toList
        case other =>
          sys.error(s"PolyFrames: expected a collection, found ${other.getClass}")
      }
    }

  /** A nested schema, decoded as a nested row. */
  implicit def cellNested[S <: HList](implicit inner: FromRow[S]): FromCell[S] =
    new FromCell[S] {
      def apply(value: Any): S = inner(value.asInstanceOf[Row], 0)
    }
}

/**
 * Decoder turning a DataFrame row into a term of the schema type `S`.
 *
 * This is what gives the function passed to the augmentation operator the
 * type `S => T` that rule 46 requires. It has no counterpart in the
 * article: it belongs to the layer binding the type system to Spark.
 *
 * Decoding is positional. Callers must therefore hand it a row whose
 * columns are laid out in the order of the declared schema, which the
 * operators guarantee by building that row themselves rather than passing
 * the DataFrame row through.
 *
 * This trait and `FromCell` are the only members of the library that are
 * serialisable, and the only ones that need to be. Everything else runs
 * while the query plan is being built, on the driver; a decoder is shipped
 * to the executors along with the function it feeds, and runs there once
 * per record. The boundary between what the type system costs at compile
 * time and what it costs at run time is exactly this one.
 */
@implicitNotFound("PolyFrames: no way to decode a row into the schema\n    ${S}")
trait FromRow[S <: HList] extends Serializable { def apply(row: Row, from: Int): S }

object FromRow {

  implicit val empty: FromRow[HNil] = new FromRow[HNil] {
    def apply(row: Row, from: Int): HNil = HNil
  }

  implicit def attribute[K, T, N <: HList](
      implicit head: FromCell[T],
      tail: FromRow[N]
  ): FromRow[FieldType[K, T] :: N] = new FromRow[FieldType[K, T] :: N] {
    def apply(row: Row, from: Int): FieldType[K, T] :: N =
      field[K](head(row.get(from))) :: tail(row, from + 1)
  }
}
