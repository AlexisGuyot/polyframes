package polyframes.infer

import shapeless.{::, =:!=, HList, HNil}
import shapeless.labelled.FieldType

import scala.annotation.implicitNotFound

/**
 * Domain of the attribute named `K` at the root of the schema `S`.
 *
 * Encodes the two `LookupFlat` rules of appendix B. `Out` is not bounded:
 * the domain found may be atomic, multi-valued or a nested schema, and the
 * rules that call upon `LookupFlat` discriminate on exactly that.
 *
 * There is no rule for `HNil`, so the reduction gets stuck when the schema
 * is exhausted without the attribute having been found. That is what makes
 * an operator applied to a missing attribute a compile-time error.
 */
@implicitNotFound(
  "PolyFrames: no attribute named ${K} at the root of the schema\n    ${S}"
)
trait LookupFlat[S <: HList, K] { type Out }

object LookupFlat {
  type Aux[S <: HList, K, O] = LookupFlat[S, K] { type Out = O }

  /** The head attribute is the one sought. */
  implicit def onHead[K, T, N <: HList]: Aux[FieldType[K, T] :: N, K, T] =
    new LookupFlat[FieldType[K, T] :: N, K] { type Out = T }

  /** The head attribute bears another name; carry on with the remainder. */
  implicit def onTail[K, K2, T2, N <: HList, O](
      implicit 
      differ: K =:!= K2,
      next: Aux[N, K, O]
  ): Aux[FieldType[K2, T2] :: N, K, O] =
    new LookupFlat[FieldType[K2, T2] :: N, K] { type Out = O }
}

/**
 * Domain of the attribute designated by the path `P` in the schema `S`.
 *
 * Encodes the three `Lookup` rules of appendix B. A path is an `HList` of
 * singleton types, so `K :: P` is the article's `Key(l) . P`.
 *
 * The two recursive rules have the same conclusion and differ only in what
 * `LookupFlat` yields for the head key: a nested schema in one case, a
 * collection of nested schemas in the other. Scala cannot discriminate two
 * instances by their premises alone, so the collection case is demoted to
 * the low-priority trait below. The compiler tries the nested-schema case
 * first and falls back to the other when it does not apply.
 */
@implicitNotFound(
  "PolyFrames: the path ${P} cannot be followed in the schema\n    ${S}"
)
trait Lookup[S <: HList, P <: HList] { type Out }

object Lookup extends LowPriorityLookup {
  type Aux[S <: HList, P <: HList, O] = Lookup[S, P] { type Out = O }

  /** Path of length one: the attribute sits at the root of `S`. */
  implicit def onLast[S <: HList, K, T](
      implicit flat: LookupFlat.Aux[S, K, T]
  ): Aux[S, K :: HNil, T] =
    new Lookup[S, K :: HNil] { type Out = T }

  /** Longer path, head key leading to a nested schema. */
  implicit def onNestedObject[S <: HList, K, P <: HList, Sub <: HList, T](
      implicit flat: LookupFlat.Aux[S, K, Sub],
      next: Aux[Sub, P, T]
  ): Aux[S, K :: P, T] =
    new Lookup[S, K :: P] { type Out = T }
}

trait LowPriorityLookup {

  /** Longer path, head key leading to a collection of nested schemas. */
  implicit def onNestedList[S <: HList, K, P <: HList, Sub <: HList, T](
      implicit 
      flat: LookupFlat.Aux[S, K, List[Sub]],
      next: Lookup.Aux[Sub, P, T]
  ): Lookup.Aux[S, K :: P, T] =
    new Lookup[S, K :: P] { type Out = T }
}
