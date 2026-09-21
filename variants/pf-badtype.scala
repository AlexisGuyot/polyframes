package polyframes.variants

import org.apache.spark.sql.SparkSession
import polyframes.bench.Record._
import polyframes.bench.Schemas._
import polyframes.bench.{Loader, Sink}
import polyframes.ops.Ops._
import polyframes.types.{Document, Relation}
import shapeless.{::, HNil}

/**
 * PolyFrames, an attribute of an unexpected domain.
 *
 * Expected: rejected at compile time.
 */
object PfBadType {

  type KeepNested =
    ("code" :: HNil) :: ("nutriscore_grade" :: HNil) ::
    ("is_beverage" :: HNil) :: ("nutriments" :: "carb_share" :: HNil) :: HNil

  type KeepFlat =
    ("code" :: HNil) :: ("nutriscore_grade" :: HNil) ::
    ("is_beverage" :: HNil) :: ("dense" :: HNil) :: HNil

  def main(args: Array[String]): Unit = {
    val path = args.headOption.getOrElse("data/products-10000.jsonl")
    val spark = SparkSession.builder().master("local[*]").appName("PfBadType").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val d0 = Loader.json[Document, SProduct](spark, path)

    // The attribute created at step 1 is given a textual domain.
    val d1 = add[Document, "nutriments" :: "carb_share" :: HNil](d0)(_ => "high")

    val d2 = add[Document, "is_beverage" :: HNil](d1)(s => s.at["pnns_groups_1"] == "Beverages")
    val d3 = project[Document, KeepNested](d2)

    // Four steps later, it is used as a number. The domain recorded at
    // step 1 has travelled through two operators to meet this one.
    val d4 = add[Document, "dense" :: HNil](d3)(s => s.at["nutriments"].at["carb_share"] > 0.5)

    println(d4.df.count())

    spark.stop()
  }
}
