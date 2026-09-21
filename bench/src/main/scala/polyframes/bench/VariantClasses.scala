package polyframes.bench

/**
 * Classes the badtransfo variants declare their result with.
 *
 * They belong here rather than inside the variants that use them, for a
 * reason worth recording. A variant is compiled into a directory of its
 * own and loaded through a class loader of its own, whereas Spark derives
 * an encoder from the type tag captured at compile time, whose mirror is
 * tied to the loader that defined the enclosing class. A case class
 * declared inside a variant is therefore invisible to encoder derivation,
 * and the variant dies of a missing class rather than of the error it was
 * written to exhibit. Setting the context class loader of the running
 * thread does not help: the mirror the tag carries is not the thread's.
 *
 * Both classes describe a pipeline result that keeps a nested attribute
 * while being announced as the flat, relational end of the pipeline. That
 * is the error; being valid case classes is what makes them valid Dataset
 * types, and what makes the error invisible to the compiler.
 */
object VariantClasses {

  case class NotFlatP1(
      code: String, nutriscore_grade: String, is_beverage: Boolean,
      dense: Boolean, nutriments: P1Dataset.CarbShare)

  case class NotFlatP2(
      barcode: String, grade_label: String, grade_rank: Double,
      nutriments: P2Dataset.NutrimentsShare)
}
