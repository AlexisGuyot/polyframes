package polyframes

import polyframes.infer._
import shapeless.{::, HNil}
import shapeless.labelled.FieldType

/**
 * Compile-time checks for the reductions of Lookup, Update and Add.
 * Nothing runs: if the file compiles, every reduction below terminates on
 * the schema written next to it.
 *
 * Each check goes through the `Aux` alias rather than the bare trait. This
 * is not a detail of style. Asking for `Add[S, P, T]` would only require
 * the reduction to be derivable, since `Out` stays abstract in that type;
 * asking for `Add.Aux[S, P, T, Expected]` requires it to terminate on
 * `Expected`, so a rule reducing to the wrong schema is rejected here
 * rather than accepted silently.
 */
object InferSpec {

  // The running example of the article, transcribed.
  type SIdentity = FieldType["names", List[String]] :: FieldType["age", Int] :: HNil
  type SDoc      = FieldType["id", Int] :: FieldType["identity", SIdentity] :: HNil

  // ---- LookupFlat and Lookup ------------------------------------------

  implicitly[LookupFlat.Aux[SDoc, "identity", SIdentity]]

  implicitly[Lookup.Aux[SDoc, "identity" :: "age" :: HNil, Int]]

  // ---- AddFlat (rules 40 and 41) --------------------------------------

  type SIdentityPlus =
    FieldType["names", List[String]] :: FieldType["age", Int] ::
      FieldType["adult", Boolean] :: HNil

  implicitly[AddFlat.Aux[SIdentity, "adult", Boolean, SIdentityPlus]]

  // ---- Add along a nested path (rules 42 and 43) ----------------------
  // This is the derivation of Equations 48 and 49 of the article: the
  // attribute is created inside the nested identity schema, and every
  // other attribute is left where it was.

  type SDocPlus =
    FieldType["id", Int] :: FieldType["identity", SIdentityPlus] :: HNil

  implicitly[Add.Aux[SDoc, "identity" :: "adult" :: HNil, Boolean, SDocPlus]]

  // ---- UpdateFlat and Update ------------------------------------------

  implicitly[UpdateFlat.Aux[
    SDoc, "id", String,
    FieldType["id", String] :: FieldType["identity", SIdentity] :: HNil
  ]]

  implicitly[Update.Aux[
    SDoc, "identity" :: "age" :: HNil, Double,
    FieldType["id", Int] :: FieldType[
      "identity",
      FieldType["names", List[String]] :: FieldType["age", Double] :: HNil
    ] :: HNil
  ]]

  // ---- Cases that must NOT compile ------------------------------------
  // Each becomes a variant under variants/ once the operators exist.

  // No attribute of that name, so LookupFlat runs out of rules:
  //   implicitly[LookupFlat[SDoc, "identty"]]

  // The attribute already exists, so rule 41 has no applicable instance:
  //   implicitly[AddFlat[SIdentity, "age", Int]]

  // The path cannot be followed: "id" is atomic, not a nested schema:
  //   implicitly[Add[SDoc, "id" :: "x" :: HNil, Int]]
}
