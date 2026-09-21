package polyframes.variants

import org.apache.spark.sql.SparkSession
import polyframes.bench.Record._
import polyframes.bench.Schemas._
import polyframes.bench.{Loader, Sink}
import polyframes.ops.Ops._
import polyframes.types.{Document, Relation}
import shapeless.{::, HNil}

/**
 * PolyFrames, an attribute absent from the schema, on the pipeline that
 * integrates two sources.
 *
 * The projection names grade_labell, which neither source declares.
 *
 * Expected: rejected at compile time.
 */
object Pf2NoAttr {

  type KeepFlat =
    ("barcode" :: HNil) :: ("grade_label" :: HNil) ::
    ("grade_rank" :: HNil) :: ("well_rated" :: HNil) :: HNil

  type KeepNested =
    ("barcode" :: HNil) :: ("grade_label" :: HNil) ::
    ("nutriments" :: "carb_share" :: HNil) :: HNil

  def main(args: Array[String]): Unit = {
    val products = args.headOption.getOrElse("data/products-10000.jsonl")
    val grades = args.drop(1).headOption.getOrElse("reference/nutriscore-grades.csv")
    val spark = SparkSession.builder().master("local[*]").appName("Pf2NoAttr").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val d0 = Loader.json[Document, SProduct](spark, products)
    val r0 = Loader.csv[Relation, SGrade](spark, grades)

    val d1 = join[Document, "nutriscore_grade" :: HNil, "grade_code" :: HNil](d0, r0)
    val d2 = drop[Document, "nutriments" :: "salt" :: HNil](d1)
    val d3 = rename[Document, "code" :: HNil, "barcode"](d2)
    val d4 = update[Document, "grade_rank" :: HNil](d3)(s => s.at["grade_rank"].toDouble / 6.0)
    val d5 = add[Document, "nutriments" :: "carb_share" :: HNil](d4) { s =>
      val n = s.at["nutriments"]
      val e = n.at["energy-kcal_100g"]
      if (e > 0.0) n.at["carbohydrates_100g"] * 4.0 / e else 0.0
    }
    val d6 = add[Document, "well_rated" :: HNil](d5)(s => s.at["grade_rank"] < 0.5)

    // grade_labell is in neither of the two joined schemas.
    val d7 = project[Relation,
      ("barcode" :: HNil) :: ("grade_labell" :: HNil) :: HNil](d6)
    Sink.typed(d7, Sink.scratch(spark, "Pf2NoAttr"))

    spark.stop()
  }
}
