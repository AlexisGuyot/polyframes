package polyframes

import org.apache.spark.sql.Row
import polyframes.runtime.{FromCell, FromRow}
import shapeless.labelled.FieldType
import shapeless.{::, HNil}

/**
 * Checks for the row decoder. Unlike the other specs, this one has
 * something to run, so it is a program rather than a set of implicit
 * summons. It needs no SparkSession: rows are built directly.
 *
 *   sbt "core/Test/runMain polyframes.FromRowSpec"
 */
object FromRowSpec {

  type SIdentity = FieldType["names", List[String]] :: FieldType["age", Int] :: HNil
  type SDoc      = FieldType["id", Int] :: FieldType["identity", SIdentity] :: HNil

  def main(args: Array[String]): Unit = {

    // A flat schema.
    val flat = implicitly[FromRow[FieldType["id", Int] :: FieldType["name", String] :: HNil]]
    val f = flat(Row(1, "chocolate"), 0)
    assert(f.head == 1, s"expected 1, got ${f.head}")
    assert(f.tail.head == "chocolate", s"expected chocolate, got ${f.tail.head}")

    // A nested schema with a collection. The nested row is decoded by the
    // same instance, reached through FromCell.cellNested.
    val nested = implicitly[FromRow[SDoc]]
    val n = nested(Row(42, Row(Seq("alexis", "robert"), 25)), 0)
    assert(n.head == 42, s"expected 42, got ${n.head}")
    val identity = n.tail.head
    assert(identity.head == List("alexis", "robert"), s"got ${identity.head}")
    assert(identity.tail.head == 25, s"got ${identity.tail.head}")

    // A missing collection decodes to the empty list rather than failing.
    val n2 = nested(Row(7, Row(null, 30)), 0)
    assert(n2.tail.head.head == Nil, "a null collection should decode to Nil")

    // Every domain the document model admits is decodable, and only those.
    implicitly[FromCell[List[String]]]
    implicitly[FromCell[SIdentity]]
    //   implicitly[FromCell[java.net.URL]]   // must not compile

    println("FromRowSpec: all checks passed")
  }
}
