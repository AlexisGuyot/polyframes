package polyframes.bench

import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, SparkSession}

/**
 * The reference pipeline, written with plain SparkSQL DataFrames.
 *
 * Same six steps, same result. Every attribute is designated by a string,
 * which the compiler cannot check: neither the existence of an attribute,
 * nor its domain, nor the model the data is in is known to it. The whole
 * of what this baseline gets wrong, it gets wrong at run time.
 */
object P1DataFrame {

  /** Declared explicitly, as a DataFrame user would, and for the same
    * reason as the typed pipeline: to avoid an inference pass over records
    * carrying thousands of fields. */
  val ReadSchema: StructType = StructType(Seq(
    StructField("code", StringType),
    StructField("product_type", StringType),
    StructField("nutriscore_grade", StringType),
    StructField("pnns_groups_1", StringType),
    StructField("completeness", DoubleType),
    StructField("countries_tags", ArrayType(StringType)),
    StructField("nutriments", StructType(Seq(
      StructField("energy-kcal_100g", DoubleType),
      StructField("fat_100g", DoubleType),
      StructField("carbohydrates_100g", DoubleType),
      StructField("proteins", DoubleType),
      StructField("salt", DoubleType)
    )))
  ))

  def apply(spark: SparkSession, path: String): DataFrame = {
    val energy = col("nutriments.`energy-kcal_100g`")
    val carbs  = col("nutriments.`carbohydrates_100g`")

    val d0 = spark.read.schema(ReadSchema).json(path)

    val d1 = d0.withColumn(
      "nutriments",
      col("nutriments").withField(
        "carb_share",
        when(energy > 0.0, carbs * 4.0 / energy).otherwise(lit(0.0))
      )
    )

    val d2 = d1.withColumn("is_beverage", col("pnns_groups_1") === "Beverages")

    val d3 = d2.select(
      col("code"),
      col("nutriscore_grade"),
      col("is_beverage"),
      struct(col("nutriments.carb_share").as("carb_share")).as("nutriments")
    )

    val d4 = d3.withColumn("dense", col("nutriments.carb_share") > 0.5)

    val d5 = d4.select("code", "nutriscore_grade", "is_beverage", "dense")

    d5.withColumn("flag", col("dense") && !col("is_beverage"))
  }

  def main(args: Array[String]): Unit = {
    val path = args.headOption.getOrElse(Workspace.data(10000))
    val spark = SparkSession.builder().master("local[*]").appName("P1DataFrame").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
    val out = apply(spark, path)
    out.printSchema()
    println(s"${out.count()} rows")
    out.show(5, truncate = false)
    Sink.untyped(out, Sink.scratch(spark, "P1DataFrame"))
    spark.stop()
  }
}
