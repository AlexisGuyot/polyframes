package polyframes.bench

import org.apache.spark.sql.{Dataset, Encoders, SparkSession}
import polyframes.bench.P1Dataset.{Nutriments, Product}

/**
 * The second pipeline, written with SparkSQL Datasets.
 *
 * Written, as in P1, in the strongest form the baseline can take: each
 * step is a `map` over a case class, so the compiler checks the attributes
 * it reads. The join goes through `joinWith`, which is the typed form and
 * yields a Dataset of pairs; the pair is then flattened by hand into a
 * class describing the joined schema.
 *
 * The cost this pipeline makes plain is not the join itself but what
 * follows it. Seven steps, seven classes, and the class describing the
 * result of the integration has to be written out in full even though it
 * is nothing but the union of two schemas already declared. That union is
 * what the inference mechanism computes and what a Dataset cannot.
 */
object P2Dataset {

  case class Grade(grade_code: String, grade_label: String, grade_rank: Long)

  case class Joined(
      code: String, product_type: String, nutriscore_grade: String,
      pnns_groups_1: String, completeness: Option[Double],
      countries_tags: Seq[String], nutriments: Option[Nutriments],
      grade_code: String, grade_label: String, grade_rank: Long)

  case class NutrimentsNoSalt(
      `energy-kcal_100g`: Option[Double], fat_100g: Option[Double],
      carbohydrates_100g: Option[Double], proteins: Option[Double])

  case class Unsalted(
      code: String, product_type: String, nutriscore_grade: String,
      pnns_groups_1: String, completeness: Option[Double],
      countries_tags: Seq[String], nutriments: Option[NutrimentsNoSalt],
      grade_code: String, grade_label: String, grade_rank: Long)

  case class Renamed(
      barcode: String, product_type: String, nutriscore_grade: String,
      pnns_groups_1: String, completeness: Option[Double],
      countries_tags: Seq[String], nutriments: Option[NutrimentsNoSalt],
      grade_code: String, grade_label: String, grade_rank: Long)

  case class Ranked(
      barcode: String, product_type: String, nutriscore_grade: String,
      pnns_groups_1: String, completeness: Option[Double],
      countries_tags: Seq[String], nutriments: Option[NutrimentsNoSalt],
      grade_code: String, grade_label: String, grade_rank: Double)

  case class NutrimentsShare(
      `energy-kcal_100g`: Option[Double], fat_100g: Option[Double],
      carbohydrates_100g: Option[Double], proteins: Option[Double],
      carb_share: Double)

  case class Shared(
      barcode: String, product_type: String, nutriscore_grade: String,
      pnns_groups_1: String, completeness: Option[Double],
      countries_tags: Seq[String], nutriments: NutrimentsShare,
      grade_code: String, grade_label: String, grade_rank: Double)

  case class Rated(
      barcode: String, product_type: String, nutriscore_grade: String,
      pnns_groups_1: String, completeness: Option[Double],
      countries_tags: Seq[String], nutriments: NutrimentsShare,
      grade_code: String, grade_label: String, grade_rank: Double,
      well_rated: Boolean)

  case class Flat(barcode: String, grade_label: String,
                  grade_rank: Double, well_rated: Boolean)

  def apply(spark: SparkSession, products: String, grades: String): Dataset[Flat] = {
    import spark.implicits._

    val p = spark.read.schema(Encoders.product[Product].schema).json(products).as[Product]
    val g = spark.read.schema(Encoders.product[Grade].schema)
      .option("header", "true").csv(grades).as[Grade]

    val d1 = p.joinWith(g, p("nutriscore_grade") === g("grade_code"), "inner")
      .map { case (a, b) =>
        Joined(a.code, a.product_type, a.nutriscore_grade, a.pnns_groups_1,
          a.completeness, a.countries_tags, a.nutriments,
          b.grade_code, b.grade_label, b.grade_rank)
      }

    val d2 = d1.map(j => Unsalted(j.code, j.product_type, j.nutriscore_grade,
      j.pnns_groups_1, j.completeness, j.countries_tags,
      j.nutriments.map(n => NutrimentsNoSalt(n.`energy-kcal_100g`, n.fat_100g,
        n.carbohydrates_100g, n.proteins)),
      j.grade_code, j.grade_label, j.grade_rank))

    val d3 = d2.map(j => Renamed(j.code, j.product_type, j.nutriscore_grade,
      j.pnns_groups_1, j.completeness, j.countries_tags, j.nutriments,
      j.grade_code, j.grade_label, j.grade_rank))

    val d4 = d3.map(j => Ranked(j.barcode, j.product_type, j.nutriscore_grade,
      j.pnns_groups_1, j.completeness, j.countries_tags, j.nutriments,
      j.grade_code, j.grade_label, j.grade_rank.toDouble / 6.0))

    val d5 = d4.map { j =>
      val e = j.nutriments.flatMap(_.`energy-kcal_100g`).getOrElse(0.0)
      val c = j.nutriments.flatMap(_.carbohydrates_100g).getOrElse(0.0)
      Shared(j.barcode, j.product_type, j.nutriscore_grade, j.pnns_groups_1,
        j.completeness, j.countries_tags,
        NutrimentsShare(j.nutriments.flatMap(_.`energy-kcal_100g`),
          j.nutriments.flatMap(_.fat_100g), j.nutriments.flatMap(_.carbohydrates_100g),
          j.nutriments.flatMap(_.proteins), if (e > 0.0) c * 4.0 / e else 0.0),
        j.grade_code, j.grade_label, j.grade_rank)
    }

    val d6 = d5.map(j => Rated(j.barcode, j.product_type, j.nutriscore_grade,
      j.pnns_groups_1, j.completeness, j.countries_tags, j.nutriments,
      j.grade_code, j.grade_label, j.grade_rank, j.grade_rank < 0.5))

    d6.map(j => Flat(j.barcode, j.grade_label, j.grade_rank, j.well_rated))
  }

  def main(args: Array[String]): Unit = {
    val products = args.headOption.getOrElse(Workspace.data(10000))
    val spark = SparkSession.builder().master("local[*]").appName("P2Dataset").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
    val out = apply(spark, products, Workspace.grades)
    out.printSchema()
    println(s"${out.count()} rows")
    out.show(5, truncate = false)
    Sink.ofDataset(out, Sink.scratch(spark, "P2Dataset"))
    spark.stop()
  }
}
