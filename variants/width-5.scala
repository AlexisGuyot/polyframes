package polyframes.variants

import org.apache.spark.sql.SparkSession
import polyframes.bench.Schemas._
import polyframes.bench.Loader
import polyframes.ops.Ops._
import polyframes.types.Document
import shapeless.{::, HNil}

/**
 * The width axis of section 5.
 *
 * The same three steps over a declared schema of 5 attributes: reading,
 * which checks the schema against the model; one augmentation, which walks
 * it; and one projection, which walks it again and merges. Nothing here
 * varies but the width of the declaration, so what the compilation time
 * measures is the length of the proof trees and nothing else.
 */
object Width5 {

  def main(args: Array[String]): Unit = {
    val path = args.headOption.getOrElse("data/products-10000.jsonl")
    val spark = SparkSession.builder().master("local[*]").appName("Width5").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val d0 = Loader.json[Document, W5](spark, path)
    val d1 = add[Document, "declared_width" :: HNil](d0)(_ => 5)
    val d2 = project[Document, ("declared_width" :: HNil) :: HNil](d1)

    println(d2.df.count())
    spark.stop()
  }
}
