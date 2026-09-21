package polyframes

import polyframes.infer._
import shapeless.{::, HNil}
import shapeless.labelled.FieldType

/**
 * Compile-time checks for the reductions of Project, Merge, Drop and
 * Rename. As in InferSpec, every check goes through `Aux`, so the expected
 * schema is required rather than merely derivability.
 */
object InferSpec2 {

  type SIdentity = FieldType["names", List[String]] :: FieldType["age", Int] :: HNil
  type SDoc      = FieldType["id", Int] :: FieldType["identity", SIdentity] :: HNil

  // ---- ProjectOne (rules 35 and 36) -----------------------------------

  implicitly[ProjectOne.Aux[SDoc, "id" :: HNil, FieldType["id", Int] :: HNil]]

  // The enclosing structure is rebuilt rather than flattened, as stated in
  // section 3.3.1: the result holds one attribute named identity, whose
  // domain is a schema holding one attribute named age.
  implicitly[ProjectOne.Aux[
    SDoc, "identity" :: "age" :: HNil,
    FieldType["identity", FieldType["age", Int] :: HNil] :: HNil
  ]]

  // ---- Merge ----------------------------------------------------------
  // Two schemas sharing a nested attribute must yield ONE such attribute
  // holding the union of its sub-attributes, not two homonymous ones.

  type Left  = FieldType["identity", FieldType["age", Int] :: HNil] :: HNil
  type Right = FieldType["identity", FieldType["names", List[String]] :: HNil] :: HNil

  implicitly[Merge.Aux[
    Left, Right,
    FieldType["identity", SIdentity] :: HNil
  ]]

  // ---- Project over a list of paths (rules 38 and 39) -----------------
  // Reduction lays out the attributes of the tail before those of the
  // head, so the projected identity attribute precedes id.

  type LProj = ("id" :: HNil) :: ("identity" :: "age" :: HNil) :: HNil

  implicitly[Project.Aux[
    SDoc, LProj,
    FieldType["identity", FieldType["age", Int] :: HNil] ::
      FieldType["id", Int] :: HNil
  ]]

  // ---- Drop and Rename ------------------------------------------------

  implicitly[Drop.Aux[
    SDoc, "identity" :: "names" :: HNil,
    FieldType["id", Int] ::
      FieldType["identity", FieldType["age", Int] :: HNil] :: HNil
  ]]

  implicitly[Rename.Aux[
    SDoc, "id" :: HNil, "code",
    FieldType["code", Int] :: FieldType["identity", SIdentity] :: HNil
  ]]

  // ---- Cases that must NOT compile ------------------------------------

  // Two atomic domains under the same name cannot be reconciled:
  //   implicitly[Merge[FieldType["a", Int] :: HNil, FieldType["a", String] :: HNil]]

  // Dropping an attribute the schema does not hold:
  //   implicitly[Drop[SDoc, "price" :: HNil]]
}
