package polyframes.bench

import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, SparkSession}

/**
 * The second pipeline, written with plain SparkSQL DataFrames.
 *
 * The join is where this baseline is furthest from the typed one. Spark is
 * told which columns to match on and nothing else; what the schema of the
 * result is, whether the two sides disagree on an attribute name, and
 * which of two homonymous columns a later step would read, are all left to
 * be discovered by running it.
 */
object P2DataFrame {

  val GradeSchema: StructType = StructType(Seq(
    StructField("grade_code", StringType),
    StructField("grade_label", StringType),
    StructField("grade_rank", LongType)
  ))

  def apply(spark: SparkSession, products: String, grades: String): DataFrame = {
    val p = spark.read.schema(P1DataFrame.ReadSchema).json(products)
    val g = spark.read.schema(GradeSchema).option("header", "true").csv(grades)

    val energy = col("nutriments.`energy-kcal_100g`")
    val carbs  = col("nutriments.`carbohydrates_100g`")

    val d1 = p.join(g, col("nutriscore_grade") === col("grade_code"), "inner")
    val d2 = d1.withColumn("nutriments", col("nutriments").dropFields("salt"))
    val d3 = d2.withColumnRenamed("code", "barcode")
    val d4 = d3.withColumn("grade_rank", col("grade_rank") / 6.0)
    val d5 = d4.withColumn("nutriments", col("nutriments").withField(
      "carb_share", when(energy > 0.0, carbs * 4.0 / energy).otherwise(lit(0.0))))
    val d6 = d5.withColumn("well_rated", col("grade_rank") < 0.5)

    d6.select("barcode", "grade_label", "grade_rank", "well_rated")
  }

  def main(args: Array[String]): Unit = {
    val products = args.headOption.getOrElse(Workspace.data(10000))
    val spark = SparkSession.builder().master("local[*]").appName("P2DataFrame").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
    val out = apply(spark, products, Workspace.grades)
    out.printSchema()
    println(s"${out.count()} rows")
    out.show(5, truncate = false)
    Sink.untyped(out, Sink.scratch(spark, "P2DataFrame"))
    spark.stop()
  }
}
