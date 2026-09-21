package polyframes.variants

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import polyframes.bench.{P1DataFrame, Sink}

/**
 * DataFrames, data in a model the operator does not accept.
 *
 * The data still carries a nested attribute when it reaches a sink that
 * can only write relational data.
 *
 * Expected: compiles; the analyser accepts it; the failure comes when the
 * file is written, several steps after the mistake.
 */
object DfBadModel {

  def main(args: Array[String]): Unit = {
    val path = args.headOption.getOrElse("data/products-10000.jsonl")
    val spark = SparkSession.builder().master("local[*]").appName("DfBadModel").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val energy = col("nutriments.`energy-kcal_100g`")
    val carbs  = col("nutriments.`carbohydrates_100g`")

    val d0 = spark.read.schema(P1DataFrame.ReadSchema).json(path)
    val d1 = d0.withColumn("nutriments", col("nutriments").withField(
      "carb_share", when(energy > 0.0, carbs * 4.0 / energy).otherwise(lit(0.0))))
    val d2 = d1.withColumn("is_beverage", col("pnns_groups_1") === "Beverages")

    Sink.untyped(d2, Sink.scratch(spark, "DfBadModel"))

    spark.stop()
  }
}
