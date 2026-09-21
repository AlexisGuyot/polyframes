package polyframes.infer

import shapeless.{::, HList, HNil}
import shapeless.labelled.FieldType

import scala.annotation.implicitNotFound

/**
 * Schema holding the single attribute designated by the path `P`, together
 * with the nesting that encloses it. Encodes rules 35 to 37 of the article.
 *
 * The enclosing structure is rebuilt rather than flattened: projecting
 * "identity" :: "age" :: HNil yields a schema holding one attribute named
 * identity, whose domain is a schema holding one attribute named age.
 *
 * Rules 36 and 37 share a conclusion and differ only in whether the head
 * key leads to a nested schema or to a collection of them, so the latter
 * sits in the low-priority trait below.
 */
@implicitNotFound(
  "PolyFrames: the path ${P} cannot be projected from the schema\n    ${S}"
)
trait ProjectOne[S <: HList, P <: HList] { type Out <: HList }

object ProjectOne extends LowPriorityProjectOne {
  type Aux[S <: HList, P <: HList, O <: HList] =
    ProjectOne[S, P] { type Out = O }

  /** Rule 35: the attribute sits at the root of `S`. */
  implicit def onLast[S <: HList, K, T](
      implicit flat: LookupFlat.Aux[S, K, T]
  ): Aux[S, K :: HNil, FieldType[K, T] :: HNil] =
    new ProjectOne[S, K :: HNil] { type Out = FieldType[K, T] :: HNil }

  /** Rule 36: head key leading to a nested schema. */
  implicit def onNestedObject[S <: HList, K, P <: HList, Sub <: HList, NSub <: HList](
      implicit 
      flat: LookupFlat.Aux[S, K, Sub],
      inner: Aux[Sub, P, NSub]
  ): Aux[S, K :: P, FieldType[K, NSub] :: HNil] =
    new ProjectOne[S, K :: P] { type Out = FieldType[K, NSub] :: HNil }
}

trait LowPriorityProjectOne {

  /** Rule 37: head key leading to a collection of nested schemas. */
  implicit def onNestedList[S <: HList, K, P <: HList, Sub <: HList, NSub <: HList](
      implicit 
      flat: LookupFlat.Aux[S, K, List[Sub]],
      inner: ProjectOne.Aux[Sub, P, NSub]
  ): ProjectOne.Aux[S, K :: P, FieldType[K, List[NSub]] :: HNil] =
    new ProjectOne[S, K :: P] { type Out = FieldType[K, List[NSub]] :: HNil }
}

/**
 * Schema holding the attributes designated by a list of paths. Encodes
 * rules 38 and 39 of the article.
 *
 * A list of paths is an `HList` of paths, themselves `HList`s of singleton
 * types, so the article's `LoPNil` is `HNil` here too. Each path is
 * projected on its own and the results are combined with `Merge`, which is
 * what allows two paths sharing a prefix to yield a single nested
 * attribute rather than two homonymous ones.
 */
@implicitNotFound(
  "PolyFrames: the paths ${L} cannot be projected from the schema\n    ${S}"
)
trait Project[S <: HList, L <: HList] { type Out <: HList }

object Project {
  type Aux[S <: HList, L <: HList, O <: HList] =
    Project[S, L] { type Out = O }

  /** Rule 38: projecting no path yields the empty schema. */
  implicit def onEmpty[S <: HList]: Aux[S, HNil, HNil] =
    new Project[S, HNil] { type Out = HNil }

  /** Rule 39: project the head path, the rest, then merge. */
  implicit def onOther[
      S <: HList, P <: HList, OP <: HList,
      NS1 <: HList, NS2 <: HList, NS <: HList
  ](
      implicit 
      head: ProjectOne.Aux[S, P, NS1],
      tail: Aux[S, OP, NS2],
      merged: Merge.Aux[NS1, NS2, NS]
  ): Aux[S, P :: OP, NS] =
    new Project[S, P :: OP] { type Out = NS }
}
