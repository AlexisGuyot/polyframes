package polyframes.variants

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import polyframes.bench.{P1DataFrame, Sink}

/**
 * DataFrames, an attribute of an unexpected domain.
 *
 * Expected: compiles; fails when the query is analysed, at run time.
 */
object DfBadType {

  def main(args: Array[String]): Unit = {
    val path = args.headOption.getOrElse("data/products-10000.jsonl")
    val spark = SparkSession.builder().master("local[*]").appName("DfBadType").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val energy = col("nutriments.`energy-kcal_100g`")
    val carbs  = col("nutriments.`carbohydrates_100g`")

    val d0 = spark.read.schema(P1DataFrame.ReadSchema).json(path)
    val d1 = d0.withColumn("nutriments", col("nutriments").withField(
      "carb_share", when(energy > 0.0, carbs * 4.0 / energy).otherwise(lit(0.0))))
    val d2 = d1.withColumn("is_beverage", col("pnns_groups_1") === "Beverages")

    // nutriscore_grade is a letter, not a number.
    val d3 = d2.withColumn("dense", col("nutriscore_grade") * 4.0 > 0.5)

    // Flattened before the sink, so that the only thing left to fail is
    // the conversion above. Writing the document as it stands would fail
    // on its multi-valued attribute first, and the variant would be
    // credited with an error it does not carry.
    val d4 = d3.select("code", "nutriscore_grade", "is_beverage", "dense")
    Sink.untyped(d4, Sink.scratch(spark, "DfBadType"))

    spark.stop()
  }
}
