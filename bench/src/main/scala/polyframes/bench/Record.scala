package polyframes.bench

import shapeless.HList
import shapeless.ops.record.Selector

/**
 * Reading an attribute of a record by name.
 *
 * The functions a pipeline hands to the augmentation operator take a term
 * of a schema type, which is a heterogeneous list. Addressing its elements
 * by position would be unreadable and would break whenever an attribute
 * moves, so they are addressed by name instead:
 *
 *   s.at["nutriscore_grade"]
 *
 * The name is resolved at compile time, and its domain determines the type
 * of the expression, so reading an attribute that does not exist, or using
 * one at the wrong domain, is a compile-time error like any other.
 */
object Record {
  implicit final class Ops[S <: HList](private val record: S) extends AnyVal {
    def at[K](implicit selector: Selector[S, K]): selector.Out = selector(record)
  }
}
