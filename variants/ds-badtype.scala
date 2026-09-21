package polyframes.variants

import org.apache.spark.sql.{Encoders, SparkSession}
import polyframes.bench.P1Dataset._
import polyframes.bench.Sink

/**
 * Datasets, an attribute of an unexpected domain.
 *
 * Expected: rejected at compile time.
 */
object DsBadType {

  def main(args: Array[String]): Unit = {
    val path = args.headOption.getOrElse("data/products-10000.jsonl")
    val spark = SparkSession.builder().master("local[*]").appName("DsBadType").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
    import spark.implicits._

    val d0 = spark.read.schema(Encoders.product[Product].schema).json(path).as[Product]
    val d1 = d0.map { p =>
      val n = p.nutriments
      val e = n.flatMap(_.`energy-kcal_100g`).getOrElse(0.0)
      val c = n.flatMap(_.carbohydrates_100g).getOrElse(0.0)
      ProductPlus(p.code, p.product_type, p.nutriscore_grade, p.pnns_groups_1,
        p.completeness, p.countries_tags,
        NutrimentsPlus(n.flatMap(_.`energy-kcal_100g`), n.flatMap(_.fat_100g),
          n.flatMap(_.carbohydrates_100g), n.flatMap(_.proteins), n.flatMap(_.salt),
          if (e > 0.0) c * 4.0 / e else 0.0))
    }
    val d2 = d1.map(p => ProductBeverage(p.code, p.product_type, p.nutriscore_grade,
      p.pnns_groups_1, p.completeness, p.countries_tags, p.nutriments,
      p.pnns_groups_1 == "Beverages"))

    // nutriscore_grade is a String; it cannot be multiplied.
    val d3 = d2.map(p => KeptDense(p.code, p.nutriscore_grade, p.is_beverage,
      CarbShare(p.nutriments.carb_share), p.nutriscore_grade * 4.0 > 0.5))
    Sink.ofDataset(d3, Sink.scratch(spark, "DsBadType"))

    spark.stop()
  }
}
