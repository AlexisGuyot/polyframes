package polyframes.bench

import org.apache.spark.sql.SparkSession
import polyframes.bench.Record._
import polyframes.bench.Schemas._
import polyframes.ops.Ops._
import polyframes.types.{Document, Relation}
import shapeless.{::, HNil}

/**
 * The second pipeline of section 5.
 *
 * Where P1 is deliberately confined to the two operators the body of the
 * article defines, P2 exercises the whole palette: the join, the removal,
 * the renaming and the domain change that appendix C introduces, alongside
 * the augmentation and the projection. It is not a longer P1. It differs
 * on three counts.
 *
 * It integrates two sources rather than transforming one. The products are
 * documents; the grades are a relational reference table. Joining them is
 * what makes the schema of the result something neither source states, and
 * the inference mechanism is what states it.
 *
 * It reaches the relational model at the end rather than in the middle.
 * The join is performed while the data is still a document, because there
 * is no reason to flatten before integrating, and the type system has no
 * trouble carrying a nested attribute across a join.
 *
 * And it changes the domain of an attribute in place, which P1 never does.
 * The rank read from the reference table is a whole number; the pipeline
 * replaces it with a fraction, and the schema follows.
 */
object P2 {

  type GradeKey   = "nutriscore_grade" :: HNil
  type CodeKey    = "grade_code" :: HNil
  type Salt       = "nutriments" :: "salt" :: HNil
  type Code       = "code" :: HNil
  type Rank       = "grade_rank" :: HNil
  type CarbShare  = "nutriments" :: "carb_share" :: HNil
  type WellRated  = "well_rated" :: HNil

  type KeepFlat =
    ("barcode" :: HNil) ::
    ("grade_label" :: HNil) ::
    ("grade_rank" :: HNil) ::
    ("well_rated" :: HNil) :: HNil

  def apply(spark: SparkSession, products: String, grades: String) = {

    val d0 = Loader.json[Document, SProduct](spark, products)
    val r0 = Loader.csv[Relation, SGrade](spark, grades)

    // 1. Integration. The schema of the result is the union of the two,
    //    which no declaration in this file states.
    val d1 = join[Document, GradeKey, CodeKey](d0, r0)

    // 2. Removal, inside the nesting.
    val d2 = drop[Document, Salt](d1)

    // 3. Renaming, at the root.
    val d3 = rename[Document, Code, "barcode"](d2)

    // 4. Change of domain, in place: a whole rank becomes a fraction.
    val d4 = update[Document, Rank](d3)(s => s.at["grade_rank"].toDouble / 6.0)

    // 5. Augmentation, inside the nesting.
    val d5 = add[Document, CarbShare](d4) { s =>
      val n = s.at["nutriments"]
      val e = n.at["energy-kcal_100g"]
      if (e > 0.0) n.at["carbohydrates_100g"] * 4.0 / e else 0.0
    }

    // 6. Augmentation reading the attribute updated at step 4.
    val d6 = add[Document, WellRated](d5)(s => s.at["grade_rank"] < 0.5)

    // 7. Flattening, announced as relational.
    project[Relation, KeepFlat](d6)
  }

  def main(args: Array[String]): Unit = {
    val products = args.headOption.getOrElse(Workspace.data(10000))
    val spark = SparkSession.builder().master("local[*]").appName("P2").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val out = apply(spark, products, Workspace.grades)
    println("--- inferred output schema ---")
    out.df.printSchema()
    println(s"--- ${out.df.count()} rows, first five ---")
    out.df.show(5, truncate = false)
    Sink.typed(out, Sink.scratch(spark, "P2"))

    spark.stop()
  }
}
