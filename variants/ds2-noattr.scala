package polyframes.variants

import org.apache.spark.sql.{Encoders, SparkSession}
import polyframes.bench.P1Dataset.{Nutriments, Product}
import polyframes.bench.P2Dataset._
import polyframes.bench.Sink

/**
 * Datasets, an attribute absent from the schema, on the pipeline that
 * integrates two sources.
 *
 * Expected: rejected at compile time.
 */
object Ds2NoAttr {

  def main(args: Array[String]): Unit = {
    val products = args.headOption.getOrElse("data/products-10000.jsonl")
    val grades = args.drop(1).headOption.getOrElse("reference/nutriscore-grades.csv")
    val spark = SparkSession.builder().master("local[*]").appName("Ds2NoAttr").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
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

    // Rated has no member of that name.
    val d7 = d6.map(j => Flat(j.barcode, j.grade_labell, j.grade_rank, j.well_rated))
    Sink.ofDataset(d7, Sink.scratch(spark, "Ds2NoAttr"))

    spark.stop()
  }
}
