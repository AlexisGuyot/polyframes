package polyframes

import polyframes.types._
import shapeless.{::, HNil}
import shapeless.labelled.FieldType

/**
 * Compile-time checks for the conformance rules.
 *
 * There is nothing to run: every assertion in this file is discharged by
 * the compiler. If the file compiles, the rules behave as the article says
 * they should. The cases that must NOT compile are kept commented out, with
 * the error the compiler is expected to report.
 */
object ValidSpec {

  type SRel = FieldType["id", Int] :: FieldType["name", String] :: HNil

  type SIdentity = FieldType["names", List[String]] :: FieldType["age", Int] :: HNil
  type SDoc      = FieldType["id", Int] :: FieldType["identity", SIdentity] :: HNil

  // A flat schema of atomic domains conforms to both models.
  implicitly[Valid[Relation, SRel]]
  implicitly[Valid[Document, SRel]]

  // A nested schema with a multi-valued attribute conforms to the document
  // model only. Resolution recurses into SIdentity on its own.
  implicitly[Valid[Document, SDoc]]

  // Must not compile: List[String] is not an atomic domain (rules 17-18).
  //   implicitly[Valid[Relation, SIdentity]]
  // expected: "List[String] is not an atomic domain"

  // Must not compile: a nested schema is not an atomic domain (rule 22 has
  // no relational counterpart).
  //   implicitly[Valid[Relation, SDoc]]

  // Must not compile: rules 15, 16, 19 and 20 all require at least one
  // attribute, so the empty schema conforms to no model.
  //   implicitly[Valid[Document, HNil]]
}
