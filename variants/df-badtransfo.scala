package polyframes.variants

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import polyframes.bench.{P1DataFrame, Sink}

/**
 * DataFrames, a transformation breaking the model the data is assumed
 * to be in.
 *
 * The projection keeps the nested attribute, and the pipeline carries on
 * as though the result were relational.
 *
 * Expected: compiles; the analyser accepts it; the failure comes when the
 * file is written.
 */
object DfBadTransfo {

  def main(args: Array[String]): Unit = {
    val path = args.headOption.getOrElse("data/products-10000.jsonl")
    val spark = SparkSession.builder().master("local[*]").appName("DfBadTransfo").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val energy = col("nutriments.`energy-kcal_100g`")
    val carbs  = col("nutriments.`carbohydrates_100g`")

    val d0 = spark.read.schema(P1DataFrame.ReadSchema).json(path)
    val d1 = d0.withColumn("nutriments", col("nutriments").withField(
      "carb_share", when(energy > 0.0, carbs * 4.0 / energy).otherwise(lit(0.0))))
    val d2 = d1.withColumn("is_beverage", col("pnns_groups_1") === "Beverages")

    val d3 = d2.select(col("code"), col("nutriscore_grade"), col("is_beverage"),
      struct(col("nutriments.carb_share").as("carb_share")).as("nutriments"))
    val d4 = d3.withColumn("dense", col("nutriments.carb_share") > 0.5)
    val d5 = d4.select("code", "nutriscore_grade", "is_beverage", "dense", "nutriments")
    Sink.untyped(d5, Sink.scratch(spark, "DfBadTransfo"))

    spark.stop()
  }
}
