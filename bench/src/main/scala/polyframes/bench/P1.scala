package polyframes.bench

import org.apache.spark.sql.SparkSession
import polyframes.ops.Ops._
import polyframes.types.{Document, Relation}
import polyframes.bench.Record._
import polyframes.bench.Schemas._
import shapeless.{::, HNil}

/**
 * The reference pipeline of section 5.
 *
 * It prepares a relational table of nutritional indicators from the raw
 * product documents, and uses nothing but the two operators the body of
 * the article defines. Six steps, each exercising a different part of the
 * type system:
 *
 *   1. augmentation inside a nested schema      rules 43 then 42
 *   2. augmentation at the root                 rule 42
 *   3. projection keeping the nesting           rules 39, 36 and 35
 *   4. augmentation reading the attribute just created inside the nesting
 *   5. projection of atomic attributes only, announced as relational
 *   6. augmentation of the now relational data
 *
 * Step 5 is where the two levels meet. Keeping only atomic attributes
 * makes the result conform to the relational model, so announcing it is
 * legitimate and the compiler agrees. Keeping the nested attribute of
 * step 1 while announcing the same model is the error the variant
 * p1-badtransfo introduces.
 */
object P1 {

  // Paths, as types. They carry no value: the operators read the names off
  // the singleton types.
  type NutrimentsCarbShare = "nutriments" :: "carb_share" :: HNil
  type IsBeverage          = "is_beverage" :: HNil
  type Dense               = "dense" :: HNil
  type Flag                = "flag" :: HNil

  type KeepNested =
    ("code" :: HNil) ::
    ("nutriscore_grade" :: HNil) ::
    ("is_beverage" :: HNil) ::
    ("nutriments" :: "carb_share" :: HNil) :: HNil

  type KeepFlat =
    ("code" :: HNil) ::
    ("nutriscore_grade" :: HNil) ::
    ("is_beverage" :: HNil) ::
    ("dense" :: HNil) :: HNil

  /** Runs the pipeline and returns the data it ends on. */
  def apply(spark: SparkSession, path: String) = {

    val d0 = Loader.json[Document, SProduct](spark, path)

    // 1. The share of the energy carried by carbohydrates, computed from
    //    two attributes of the nested nutriments schema and written back
    //    into it.
    val d1 = add[Document, NutrimentsCarbShare](d0) { s =>
      val n = s.at["nutriments"]
      val energy = n.at["energy-kcal_100g"]
      if (energy > 0.0) n.at["carbohydrates_100g"] * 4.0 / energy else 0.0
    }

    // 2. Whether the product is a beverage, from a top-level attribute.
    val d2 = add[Document, IsBeverage](d1)(s => s.at["pnns_groups_1"] == "Beverages")

    // 3. Keep four attributes, one of them nested. The result is still a
    //    document, and could not be announced otherwise.
    val d3 = project[Document, KeepNested](d2)

    // 4. Read the attribute created at step 1, now through the projection.
    val d4 = add[Document, Dense](d3)(s => s.at["nutriments"].at["carb_share"] > 0.5)

    // 5. Keep only atomic attributes, and announce relational data.
    val d5 = project[Relation, KeepFlat](d4)

    // 6. The guarantee survives the change of model.
    add[Relation, Flag](d5)(s => s.at["dense"] && !s.at["is_beverage"])
  }

  def main(args: Array[String]): Unit = {
    val path = args.headOption.getOrElse(Workspace.data(10000))
    val spark = SparkSession.builder().master("local[*]").appName("P1").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val out = apply(spark, path)
    println("--- inferred output schema ---")
    out.df.printSchema()
    println(s"--- ${out.df.count()} rows, first five ---")
    out.df.show(5, truncate = false)
    Sink.typed(out, Sink.scratch(spark, "P1"))

    spark.stop()
  }
}
