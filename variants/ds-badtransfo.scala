package polyframes.variants

import org.apache.spark.sql.{Encoders, SparkSession}
import polyframes.bench.P1Dataset._
import polyframes.bench.Sink
import polyframes.bench.VariantClasses.NotFlatP1

/**
 * Datasets, a transformation breaking the model the data is assumed to
 * be in.
 *
 * The class the pipeline ends on keeps a nested attribute. Being a valid
 * case class, it is a valid Dataset type.
 *
 * Expected: compiles; fails when the file is written, at run time.
 */
object DsBadTransfo {

  def main(args: Array[String]): Unit = {
    val path = args.headOption.getOrElse("data/products-10000.jsonl")
    val spark = SparkSession.builder().master("local[*]").appName("DsBadTransfo").getOrCreate()
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

    // NotFlatP1 is declared as the flat result of the pipeline, yet keeps
    // a nested attribute. Being a valid case class, it is a valid Dataset
    // type, and the compiler has nothing to say about it.
    val d3 = d2.map(p => NotFlatP1(p.code, p.nutriscore_grade, p.is_beverage,
      p.nutriments.carb_share > 0.5, CarbShare(p.nutriments.carb_share)))
    Sink.ofDataset(d3, Sink.scratch(spark, "DsBadTransfo"))

    spark.stop()
  }
}
