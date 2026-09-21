package polyframes.bench

import org.apache.spark.sql.SparkSession
import polyframes.runtime.{Data, Shape, SparkSchema}
import polyframes.types.{Model, Valid}
import shapeless.HList

/**
 * Reads a JSONL file into typed data.
 *
 * The declared schema is handed to the reader rather than inferred from
 * the file. Two things follow. The reader does not have to walk the data
 * to guess a schema, which on this dump means not discovering its 2231
 * top-level fields; and an attribute whose declared domain disagrees with
 * the file is read as null rather than blowing up later, which keeps a
 * mistaken declaration from being mistaken for a failure of the pipeline.
 *
 * Note what is and is not guaranteed here. The type system checks the
 * declared schema against the announced model, and the pipeline against
 * the declared schema. Nothing checks the declared schema against the
 * data: a declaration that does not describe the file is a premise the
 * whole edifice rests on, and it is the user's to get right.
 */
object Loader {

  def json[M <: Model, S <: HList](spark: SparkSession, path: String)(
      implicit valid: Valid[M, S],
      shape: Shape[S],
      schema: SparkSchema[S]
  ): Data[M, S] =
    Data[M, S](spark.read.schema(schema.structType).json(path))

  /** A delimited file, read the same way and for the same reasons. */
  def csv[M <: Model, S <: HList](spark: SparkSession, path: String)(
      implicit valid: Valid[M, S],
      shape: Shape[S],
      schema: SparkSchema[S]
  ): Data[M, S] =
    Data[M, S](
      spark.read.schema(schema.structType).option("header", "true").csv(path)
    )
}
