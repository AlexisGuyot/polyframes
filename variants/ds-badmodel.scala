package polyframes.variants

import org.apache.spark.sql.{Encoders, SparkSession}
import polyframes.bench.P1Dataset._
import polyframes.bench.Sink

/**
 * Datasets, data in a model the operator does not accept.
 *
 * The class describes a nested schema, and the sink writes CSV. A Dataset
 * records no model, so nothing here stands out.
 *
 * Expected: compiles; fails when the file is written, at run time.
 */
object DsBadModel {

  def main(args: Array[String]): Unit = {
    val path = args.headOption.getOrElse("data/products-10000.jsonl")
    val spark = SparkSession.builder().master("local[*]").appName("DsBadModel").getOrCreate()
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

    Sink.ofDataset(d2, Sink.scratch(spark, "DsBadModel"))

    spark.stop()
  }
}
