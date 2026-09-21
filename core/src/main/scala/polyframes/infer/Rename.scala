package polyframes.infer

import shapeless.{::, =:!=, HList, HNil}
import shapeless.labelled.FieldType

import scala.annotation.implicitNotFound

/**
 * Schema obtained by giving the root attribute named `KOld` the new name
 * `KNew`. Encodes the two `RenameFlat` rules of appendix B. The domain and
 * the position of the attribute are preserved.
 */
@implicitNotFound(
  "PolyFrames: no attribute named ${KOld} at the root of the schema\n    ${S}\n" +
  "  so it cannot be renamed to ${KNew}."
)
trait RenameFlat[S <: HList, KOld, KNew] { type Out <: HList }

object RenameFlat {
  type Aux[S <: HList, KOld, KNew, O <: HList] =
    RenameFlat[S, KOld, KNew] { type Out = O }

  implicit def onHead[KOld, KNew, T, N <: HList]
      : Aux[FieldType[KOld, T] :: N, KOld, KNew, FieldType[KNew, T] :: N] =
    new RenameFlat[FieldType[KOld, T] :: N, KOld, KNew] {
      type Out = FieldType[KNew, T] :: N
    }

  implicit def onTail[KOld, KNew, K2, T2, N <: HList, NN <: HList](
      implicit 
      differ: KOld =:!= K2,
      next: Aux[N, KOld, KNew, NN]
  ): Aux[FieldType[K2, T2] :: N, KOld, KNew, FieldType[K2, T2] :: NN] =
    new RenameFlat[FieldType[K2, T2] :: N, KOld, KNew] {
      type Out = FieldType[K2, T2] :: NN
    }
}

/** Renaming of the attribute designated by a path. Appendix B, `Rename`. */
@implicitNotFound(
  "PolyFrames: the path ${P} cannot be followed in the schema\n    ${S}"
)
trait Rename[S <: HList, P <: HList, KNew] { type Out <: HList }

object Rename extends LowPriorityRename {
  type Aux[S <: HList, P <: HList, KNew, O <: HList] =
    Rename[S, P, KNew] { type Out = O }

  implicit def onLast[S <: HList, KOld, KNew, NS <: HList](
      implicit flat: RenameFlat.Aux[S, KOld, KNew, NS]
  ): Aux[S, KOld :: HNil, KNew, NS] =
    new Rename[S, KOld :: HNil, KNew] { type Out = NS }

  implicit def onNestedObject[
      S <: HList, K, P <: HList, KNew,
      Sub <: HList, NSub <: HList, NS <: HList
  ](
      implicit 
      flat: LookupFlat.Aux[S, K, Sub],
      inner: Aux[Sub, P, KNew, NSub],
      back: UpdateFlat.Aux[S, K, NSub, NS]
  ): Aux[S, K :: P, KNew, NS] =
    new Rename[S, K :: P, KNew] { type Out = NS }
}

trait LowPriorityRename {
  implicit def onNestedList[
      S <: HList, K, P <: HList, KNew,
      Sub <: HList, NSub <: HList, NS <: HList
  ](
      implicit 
      flat: LookupFlat.Aux[S, K, List[Sub]],
      inner: Rename.Aux[Sub, P, KNew, NSub],
      back: UpdateFlat.Aux[S, K, List[NSub], NS]
  ): Rename.Aux[S, K :: P, KNew, NS] =
    new Rename[S, K :: P, KNew] { type Out = NS }
}
