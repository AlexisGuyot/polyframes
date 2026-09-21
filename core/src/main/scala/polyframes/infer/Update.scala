package polyframes.infer

import shapeless.{::, =:!=, HList, HNil}
import shapeless.labelled.FieldType

import scala.annotation.implicitNotFound

/**
 * Schema obtained by giving the root attribute named `K` the new domain
 * `TNew`. Encodes the two `UpdateFlat` rules of appendix B.
 *
 * Nothing else changes: the name of the attribute, its position and every
 * other attribute are preserved. As with `LookupFlat`, there is no rule for
 * `HNil`, so updating an attribute a schema does not hold is rejected.
 */
@implicitNotFound(
  "PolyFrames: no attribute named ${K} at the root of the schema\n    ${S}\n" +
  "  so its domain cannot be changed to ${TNew}."
)
trait UpdateFlat[S <: HList, K, TNew] { type Out <: HList }

object UpdateFlat {
  type Aux[S <: HList, K, TNew, O <: HList] =
    UpdateFlat[S, K, TNew] { type Out = O }

  /** The head attribute is the one to update. */
  implicit def onHead[K, TOld, TNew, N <: HList]
      : Aux[FieldType[K, TOld] :: N, K, TNew, FieldType[K, TNew] :: N] =
    new UpdateFlat[FieldType[K, TOld] :: N, K, TNew] {
      type Out = FieldType[K, TNew] :: N
    }

  /** The head attribute bears another name; keep it and carry on. */
  implicit def onTail[K, TNew, K2, T2, N <: HList, NN <: HList](
      implicit 
      differ: K =:!= K2,
      next: Aux[N, K, TNew, NN]
  ): Aux[FieldType[K2, T2] :: N, K, TNew, FieldType[K2, T2] :: NN] =
    new UpdateFlat[FieldType[K2, T2] :: N, K, TNew] {
      type Out = FieldType[K2, T2] :: NN
    }
}

/**
 * Schema obtained by giving the attribute designated by the path `P` the
 * new domain `TNew`. Encodes the three `Update` rules of appendix B.
 *
 * The recursive rules descend into the nesting with `LookupFlat`, update
 * the sub-schema, then write it back with `UpdateFlat`. As in `Lookup`, the
 * collection case is demoted to a low-priority trait.
 */
@implicitNotFound(
  "PolyFrames: the path ${P} cannot be followed in the schema\n    ${S}"
)
trait Update[S <: HList, P <: HList, TNew] { type Out <: HList }

object Update extends LowPriorityUpdate {
  type Aux[S <: HList, P <: HList, TNew, O <: HList] =
    Update[S, P, TNew] { type Out = O }

  /** Path of length one: delegate to `UpdateFlat`. */
  implicit def onLast[S <: HList, K, TNew, NS <: HList](
      implicit flat: UpdateFlat.Aux[S, K, TNew, NS]
  ): Aux[S, K :: HNil, TNew, NS] =
    new Update[S, K :: HNil, TNew] { type Out = NS }

  /** Longer path, head key leading to a nested schema. */
  implicit def onNestedObject[
      S <: HList, K, P <: HList, TNew,
      Sub <: HList, NSub <: HList, NS <: HList
  ](
      implicit 
      flat: LookupFlat.Aux[S, K, Sub],
      inner: Aux[Sub, P, TNew, NSub],
      back: UpdateFlat.Aux[S, K, NSub, NS]
  ): Aux[S, K :: P, TNew, NS] =
    new Update[S, K :: P, TNew] { type Out = NS }
}

trait LowPriorityUpdate {

  /** Longer path, head key leading to a collection of nested schemas. */
  implicit def onNestedList[
      S <: HList, K, P <: HList, TNew,
      Sub <: HList, NSub <: HList, NS <: HList
  ](
      implicit 
      flat: LookupFlat.Aux[S, K, List[Sub]],
      inner: Update.Aux[Sub, P, TNew, NSub],
      back: UpdateFlat.Aux[S, K, List[NSub], NS]
  ): Update.Aux[S, K :: P, TNew, NS] =
    new Update[S, K :: P, TNew] { type Out = NS }
}
