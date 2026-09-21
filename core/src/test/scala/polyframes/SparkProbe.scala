package polyframes

import org.apache.spark.sql.api.java.UDF1
import org.apache.spark.sql.functions.{col, expr, lit}
import org.apache.spark.sql.types._
import org.apache.spark.sql.{Row, SparkSession}
import polyframes.runtime._
import shapeless.labelled.FieldType
import shapeless.{::, HNil}

import scala.jdk.CollectionConverters._

/**
 * Probe for the four SparkSQL facilities the operators are about to be
 * built on. It exists so that a failure is attributed to one identified
 * facility rather than to a whole module.
 *
 *   sbt "core/Test/runMain polyframes.SparkProbe"
 *
 * This is the first program that opens a SparkSession, and therefore the
 * first that needs the Hadoop native binaries on Windows.
 */
object SparkProbe {

  type SIdentity = FieldType["names", List[String]] :: FieldType["age", Int] :: HNil
  type SDoc      = FieldType["id", Int] :: FieldType["identity", SIdentity] :: HNil

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().master("local[*]").appName("probe").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val identity = StructType(Seq(
      StructField("age", IntegerType),
      StructField("names", ArrayType(StringType))
    ))
    val schema = StructType(Seq(
      StructField("identity", identity),
      StructField("id", IntegerType)
    ))
    val rows = List(
      Row(Row(25, Seq("alexis", "robert")), 42),
      Row(Row(30, Seq("maeva")), 7)
    )
    val df = spark.createDataFrame(rows.asJava, schema)

    // 1. Shape: the DataFrame is laid out in schema order, note that the
    //    source has identity before id, and age before names.
    val laid = df.select(Shape[SDoc].columns(n => col(s"`$n`")): _*)
    val got = laid.schema.fields.map(_.name).toList
    assert(got == List("id", "identity"), s"top level order: $got")
    val sub = laid.schema("identity").dataType.asInstanceOf[StructType].fields.map(_.name).toList
    assert(sub == List("names", "age"), s"nested order: $sub")
    println("1. Shape ok")

    // 2. Target: insertion of a nested field.
    val tgt = implicitly[Target["identity" :: "adult" :: HNil]]
    val withAdult = tgt.insert(laid, lit(true))
    assert(withAdult.schema("identity").dataType.asInstanceOf[StructType].fieldNames.contains("adult"))
    println("2. Target.insert ok")

    // 3. Target: removal and renaming of a nested field.
    assert(!tgt.remove(withAdult).schema("identity").dataType
      .asInstanceOf[StructType].fieldNames.contains("adult"))
    val renamed = implicitly[Target["identity" :: "age" :: HNil]].renameTo(laid, "years")
    assert(renamed.schema("identity").dataType.asInstanceOf[StructType].fieldNames.contains("years"))
    println("3. Target.remove and renameTo ok")

    // 4. A UDF taking the whole record, registered through the Java
    //    interface and invoked by name. This is what gives the function of
    //    rule 46 the type S => T, without any legacy setting.
    val decode = implicitly[FromRow[SDoc]]
    val fn: SDoc => Boolean = s => s.tail.head.tail.head >= 18
    spark.udf.register(
      "pf_probe",
      new UDF1[Row, Boolean] { def call(r: Row): Boolean = fn(decode(r, 0)) },
      SparkType[Boolean].dataType
    )
    val applied = laid.withColumn("adult", expr("pf_probe(struct(*))"))
    val out = applied.select("id", "adult").collect().map(r => (r.getInt(0), r.getBoolean(1))).toList
    assert(out.toSet == Set((42, true), (7, true)), s"udf output: $out")
    println("4. record-level UDF ok")

    spark.stop()
    println("SparkProbe: all checks passed")
  }
}
