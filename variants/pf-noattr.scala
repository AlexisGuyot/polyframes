package polyframes.variants

import org.apache.spark.sql.SparkSession
import polyframes.bench.Record._
import polyframes.bench.Schemas._
import polyframes.bench.{Loader, Sink}
import polyframes.ops.Ops._
import polyframes.types.{Document, Relation}
import shapeless.{::, HNil}

/**
 * PolyFrames, an attribute absent from the schema.
 *
 * Step 3 projects an attribute named cod, which SProduct does not have.
 *
 * Expected: rejected at compile time.
 */
object PfNoAttr {

  type KeepNested =
    ("code" :: HNil) :: ("nutriscore_grade" :: HNil) ::
    ("is_beverage" :: HNil) :: ("nutriments" :: "carb_share" :: HNil) :: HNil

  type KeepFlat =
    ("code" :: HNil) :: ("nutriscore_grade" :: HNil) ::
    ("is_beverage" :: HNil) :: ("dense" :: HNil) :: HNil

  def main(args: Array[String]): Unit = {
    val path = args.headOption.getOrElse("data/products-10000.jsonl")
    val spark = SparkSession.builder().master("local[*]").appName("PfNoAttr").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val d0 = Loader.json[Document, SProduct](spark, path)
    val d1 = add[Document, "nutriments" :: "carb_share" :: HNil](d0) { s =>
      val n = s.at["nutriments"]
      val e = n.at["energy-kcal_100g"]
      if (e > 0.0) n.at["carbohydrates_100g"] * 4.0 / e else 0.0
    }
    val d2 = add[Document, "is_beverage" :: HNil](d1)(s => s.at["pnns_groups_1"] == "Beverages")

    // The projected path names an attribute the schema does not hold: the
    // reduction of LookupFlat runs out of rules and gets stuck.
    val d3 = project[Document, ("cod" :: HNil) :: ("nutriscore_grade" :: HNil) :: HNil](d2)

    println(d3.df.count())

    spark.stop()
  }
}
