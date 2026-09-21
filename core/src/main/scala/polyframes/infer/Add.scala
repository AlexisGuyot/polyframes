package polyframes.infer

import shapeless.{::, =:!=, HList, HNil}
import shapeless.labelled.FieldType

import scala.annotation.implicitNotFound

/**
 * Schema obtained by appending an attribute named `K` of domain `T` at a
 * single level of nesting. Encodes rules 40 and 41 of the article.
 *
 * The `=:!=` witness is the side condition l != l' of rule 41. It is not
 * there to disambiguate the two instances, which are already disjoint, but
 * to carry the meaning of the rule: without it the traversal would walk
 * past an attribute of the same name and append a duplicate. With it, no
 * instance applies once the name is found, the reduction gets stuck, and
 * uniqueness of attribute names is enforced by construction.
 */
@implicitNotFound(
  "PolyFrames: cannot add an attribute named ${K} to the schema\n    ${S}\n" +
  "  Either the schema already holds an attribute of that name, or the " +
  "path leading to it cannot be followed."
)
trait AddFlat[S <: HList, K, T] { type Out <: HList }

object AddFlat {
  type Aux[S <: HList, K, T, O <: HList] = AddFlat[S, K, T] { type Out = O }

  /** Rule 40: appending to the empty schema. */
  implicit def onEmpty[K, T]: Aux[HNil, K, T, FieldType[K, T] :: HNil] =
    new AddFlat[HNil, K, T] { type Out = FieldType[K, T] :: HNil }

  /** Rule 41: keep the head attribute, provided it bears another name. */
  implicit def onOther[K, T, K2, T2, N <: HList, NN <: HList](
      implicit 
      differ: K =:!= K2,
      next: Aux[N, K, T, NN]
  ): Aux[FieldType[K2, T2] :: N, K, T, FieldType[K2, T2] :: NN] =
    new AddFlat[FieldType[K2, T2] :: N, K, T] {
      type Out = FieldType[K2, T2] :: NN
    }
}

/**
 * Schema obtained by creating the attribute designated by the path `P`,
 * with domain `T`. Encodes rules 42 to 44 of the article.
 *
 * The last key of the path is the name of the attribute to create; the
 * keys before it locate the sub-schema to insert it into. Rule 42 applies
 * when the path has length one and delegates to `AddFlat`. Rules 43 and 44
 * descend with `LookupFlat`, add into the sub-schema, then write it back
 * with `UpdateFlat`; they share a conclusion, so the collection case sits
 * in the low-priority trait below.
 */
@implicitNotFound(
  "PolyFrames: cannot add the attribute at path ${P} of domain ${T} to the " +
  "schema\n    ${S}"
)
trait Add[S <: HList, P <: HList, T] { type Out <: HList }

object Add extends LowPriorityAdd {
  type Aux[S <: HList, P <: HList, T, O <: HList] =
    Add[S, P, T] { type Out = O }

  /** Rule 42. */
  implicit def onLast[S <: HList, K, T, NS <: HList](
      implicit 
      flat: AddFlat.Aux[S, K, T, NS]
  ): Aux[S, K :: HNil, T, NS] =
    new Add[S, K :: HNil, T] { type Out = NS }

  /** Rule 43. */
  implicit def onNestedObject[
      S <: HList, K, P <: HList, T,
      Sub <: HList, NSub <: HList, NS <: HList
  ](
      implicit 
      flat: LookupFlat.Aux[S, K, Sub],
      inner: Aux[Sub, P, T, NSub],
      back: UpdateFlat.Aux[S, K, NSub, NS]
  ): Aux[S, K :: P, T, NS] =
    new Add[S, K :: P, T] { type Out = NS }
}

trait LowPriorityAdd {

  /** Rule 44. */
  implicit def onNestedList[
      S <: HList, K, P <: HList, T,
      Sub <: HList, NSub <: HList, NS <: HList
  ](
      implicit 
      flat: LookupFlat.Aux[S, K, List[Sub]],
      inner: Add.Aux[Sub, P, T, NSub],
      back: UpdateFlat.Aux[S, K, List[NSub], NS]
  ): Add.Aux[S, K :: P, T, NS] =
    new Add[S, K :: P, T] { type Out = NS }
}
