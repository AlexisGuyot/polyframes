package polyframes

import org.apache.spark.sql.types._
import org.apache.spark.sql.{Row, SparkSession}
import polyframes.ops.Ops._
import polyframes.runtime.Data
import polyframes.types.{Document, Relation}
import shapeless.labelled.FieldType
import shapeless.{::, HNil}

import scala.jdk.CollectionConverters._

/**
 * End-to-end probe for the six operators, on a DataFrame small enough to
 * be read by eye. It rehearses the shape of the reference pipeline of
 * section 5 without touching the real dataset.
 *
 *   sbt "core/Test/runMain polyframes.OpsProbe"
 */
object OpsProbe {

  type SIdentity = FieldType["names", List[String]] :: FieldType["age", Int] :: HNil
  type SDoc      = FieldType["id", Int] :: FieldType["identity", SIdentity] :: HNil

  // A relational table to join against. Note that it shares no attribute
  // name with SDoc: Merge would otherwise have no applicable rule.
  type SCity = FieldType["person", Int] :: FieldType["city", String] :: HNil

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().master("local[*]").appName("ops").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val identity = StructType(Seq(
      StructField("names", ArrayType(StringType)),
      StructField("age", IntegerType)
    ))
    val docSchema = StructType(Seq(
      StructField("id", IntegerType), StructField("identity", identity)
    ))
    val docs = spark.createDataFrame(
      List(
        Row(42, Row(Seq("alexis", "robert"), 25)),
        Row(7, Row(Seq("maeva"), 30))
      ).asJava,
      docSchema
    )
    val d0 = Data[Document, SDoc](docs)

    // 1. Augmentation inside the nested schema (rules 43 and 42).
    val d1 = add[Document, "identity" :: "adult" :: HNil](d0)(
      s => s.tail.head.tail.head >= 18
    )
    assert(d1.df.schema("identity").dataType.asInstanceOf[StructType]
      .fieldNames.toList == List("names", "age", "adult"), "nested add")
    println("1. add, nested ok")

    // 2. Augmentation at the root.
    val d2 = add[Document, "known" :: HNil](d1)(_ => true)
    assert(d2.df.schema.fieldNames.toList == List("id", "identity", "known"), "root add")
    println("2. add, root ok")

    // 3. Projection changing the announced model. Keeping only atomic
    //    attributes yields relational data; keeping identity would not.
    val d3 = project[Relation, ("id" :: HNil) :: ("known" :: HNil) :: HNil](d2)
    assert(d3.df.schema.fieldNames.toSet == Set("id", "known"), "project")
    println("3. project to Relation ok")

    // 4. Removal and renaming.
    val d4 = drop[Document, "identity" :: "names" :: HNil](d2)
    assert(!d4.df.schema("identity").dataType.asInstanceOf[StructType]
      .fieldNames.contains("names"), "drop")
    val d5 = rename[Document, "id" :: HNil, "code"](d4)
    assert(d5.df.schema.fieldNames.contains("code"), "rename")
    println("4. drop and rename ok")

    // 5. Inner join with a relational table.
    val cities = spark.createDataFrame(
      List(Row(42, "Dijon"), Row(7, "Marseille")).asJava,
      StructType(Seq(StructField("person", IntegerType), StructField("city", StringType)))
    )
    val c0 = Data[Relation, SCity](cities)
    val joined = join[Document, "id" :: HNil, "person" :: HNil](d2, c0)
    val out = joined.df.select("id", "city").collect()
      .map(r => (r.getInt(0), r.getString(1))).toSet
    assert(out == Set((42, "Dijon"), (7, "Marseille")), s"join output: $out")
    println("5. inner join ok")

    spark.stop()
    println("OpsProbe: all checks passed")
  }
}
