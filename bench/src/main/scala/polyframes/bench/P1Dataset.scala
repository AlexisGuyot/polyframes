package polyframes.bench

import org.apache.spark.sql.{Dataset, Encoders, SparkSession}

/**
 * The reference pipeline, written with SparkSQL Datasets.
 *
 * This is the strongest form the baseline can take. Each step is a `map`
 * over a case class, so attributes are members of an object and the
 * compiler does check that they exist and that their domain is the one
 * being used. That is what a Dataset is for.
 *
 * Three costs come with it, all visible below.
 *
 * A Dataset is typed by one class, so an operator that changes the schema
 * needs a new one, declared by hand and kept consistent with the previous
 * by hand as well. Six steps, six classes, and every attribute carried
 * through is copied across each of them. The type of the data is stated
 * afresh at each step rather than inferred from the previous one, which is
 * what the inference mechanism of the article removes.
 *
 * An attribute that may be missing has to be modelled as such. A `Double`
 * member of a case class is a primitive, and Spark refuses to put a null
 * in one, so the nutritional attributes are wrapped in `Option` and every
 * use of them is a `flatMap` and a default. The other two pipelines say
 * nothing about optionality and behave as if a missing value were a zero,
 * which is the simplification the article makes and does not hide.
 *
 * Finally, two things stay outside its reach whatever the effort. A
 * Dataset does not record which model its data is in, all of them being
 * tabular from its point of view; and nothing prevents a class from
 * describing a schema the announced model would not admit.
 */
object P1Dataset {

  case class Nutriments(
      `energy-kcal_100g`: Option[Double], fat_100g: Option[Double],
      carbohydrates_100g: Option[Double], proteins: Option[Double],
      salt: Option[Double]
  )
  case class Product(
      code: String, product_type: String, nutriscore_grade: String,
      pnns_groups_1: String, completeness: Option[Double],
      countries_tags: Seq[String], nutriments: Option[Nutriments]
  )

  case class NutrimentsPlus(
      `energy-kcal_100g`: Option[Double], fat_100g: Option[Double],
      carbohydrates_100g: Option[Double], proteins: Option[Double],
      salt: Option[Double], carb_share: Double
  )
  case class ProductPlus(
      code: String, product_type: String, nutriscore_grade: String,
      pnns_groups_1: String, completeness: Option[Double],
      countries_tags: Seq[String], nutriments: NutrimentsPlus
  )
  case class ProductBeverage(
      code: String, product_type: String, nutriscore_grade: String,
      pnns_groups_1: String, completeness: Option[Double],
      countries_tags: Seq[String], nutriments: NutrimentsPlus,
      is_beverage: Boolean
  )
  case class CarbShare(carb_share: Double)
  case class Kept(code: String, nutriscore_grade: String,
                  is_beverage: Boolean, nutriments: CarbShare)
  case class KeptDense(code: String, nutriscore_grade: String,
                       is_beverage: Boolean, nutriments: CarbShare, dense: Boolean)
  case class Flat(code: String, nutriscore_grade: String,
                  is_beverage: Boolean, dense: Boolean)
  case class Flagged(code: String, nutriscore_grade: String,
                     is_beverage: Boolean, dense: Boolean, flag: Boolean)

  def apply(spark: SparkSession, path: String): Dataset[Flagged] = {
    import spark.implicits._

    val d0 = spark.read.schema(Encoders.product[Product].schema).json(path).as[Product]

    val d1 = d0.map { p =>
      val n = p.nutriments
      val energy = n.flatMap(_.`energy-kcal_100g`).getOrElse(0.0)
      val carbs  = n.flatMap(_.carbohydrates_100g).getOrElse(0.0)
      val share  = if (energy > 0.0) carbs * 4.0 / energy else 0.0
      ProductPlus(p.code, p.product_type, p.nutriscore_grade, p.pnns_groups_1,
        p.completeness, p.countries_tags,
        NutrimentsPlus(n.flatMap(_.`energy-kcal_100g`), n.flatMap(_.fat_100g),
          n.flatMap(_.carbohydrates_100g), n.flatMap(_.proteins),
          n.flatMap(_.salt), share))
    }

    val d2 = d1.map(p => ProductBeverage(p.code, p.product_type, p.nutriscore_grade,
      p.pnns_groups_1, p.completeness, p.countries_tags, p.nutriments,
      p.pnns_groups_1 == "Beverages"))

    val d3 = d2.map(p => Kept(p.code, p.nutriscore_grade, p.is_beverage,
      CarbShare(p.nutriments.carb_share)))

    val d4 = d3.map(p => KeptDense(p.code, p.nutriscore_grade, p.is_beverage,
      p.nutriments, p.nutriments.carb_share > 0.5))

    val d5 = d4.map(p => Flat(p.code, p.nutriscore_grade, p.is_beverage, p.dense))

    d5.map(p => Flagged(p.code, p.nutriscore_grade, p.is_beverage, p.dense,
      p.dense && !p.is_beverage))
  }

  def main(args: Array[String]): Unit = {
    val path = args.headOption.getOrElse(Workspace.data(10000))
    val spark = SparkSession.builder().master("local[*]").appName("P1Dataset").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
    val out = apply(spark, path)
    out.printSchema()
    println(s"${out.count()} rows")
    out.show(5, truncate = false)
    Sink.ofDataset(out, Sink.scratch(spark, "P1Dataset"))
    spark.stop()
  }
}
