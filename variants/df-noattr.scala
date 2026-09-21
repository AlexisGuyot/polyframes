package polyframes.variants

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import polyframes.bench.{P1DataFrame, Sink}

/**
 * DataFrames, an attribute absent from the schema.
 *
 * The attribute is named by a string, which the compiler does not read.
 *
 * Expected: compiles; fails when the query is analysed, at run time.
 */
object DfNoAttr {

  def main(args: Array[String]): Unit = {
    val path = args.headOption.getOrElse("data/products-10000.jsonl")
    val spark = SparkSession.builder().master("local[*]").appName("DfNoAttr").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val energy = col("nutriments.`energy-kcal_100g`")
    val carbs  = col("nutriments.`carbohydrates_100g`")

    val d0 = spark.read.schema(P1DataFrame.ReadSchema).json(path)
    val d1 = d0.withColumn("nutriments", col("nutriments").withField(
      "carb_share", when(energy > 0.0, carbs * 4.0 / energy).otherwise(lit(0.0))))
    val d2 = d1.withColumn("is_beverage", col("pnns_groups_1") === "Beverages")

    // cod does not exist. Nothing says so until the query is analysed.
    val d3 = d2.select(col("cod"), col("nutriscore_grade"))
    Sink.untyped(d3, Sink.scratch(spark, "DfNoAttr"))

    spark.stop()
  }
}
