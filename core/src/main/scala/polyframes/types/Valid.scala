package polyframes.types

import shapeless.{::, HList, HNil}
import shapeless.labelled.FieldType

import scala.annotation.implicitNotFound

/**
 * Conformance of a schema to a model.
 *
 * Encodes rules 15, 16, 19 and 20 of the article. A value of type
 * `Valid[M, S]` is evidence that the schema type `S` satisfies the
 * constraints the model `M` imposes at the schema level. It carries no data.
 *
 * Both halves follow the recursive shape of `HList`: a base case for a
 * schema holding a single attribute, and a recursive case that peels one
 * attribute off and requires the remainder to be conformant in turn. There
 * is deliberately no instance for `HNil`, since rules 15, 16, 19 and 20 all
 * require at least one attribute.
 *
 * The base cases are more specific than the recursive ones, so the compiler
 * prefers them for one-attribute schemas without ambiguity.
 */
@implicitNotFound(
  "PolyFrames: the schema\n    ${S}\n  does not conform to the model ${M}. " +
  "Check the domain of every attribute, and, for the relational model, that " +
  "no attribute is nested or multi-valued."
)
trait Valid[M <: Model, S <: HList]

object Valid {

  // ---- Relational model (rules 15 and 16) ----------------------------

  /** Rule 15: a single attribute whose domain is allowed by the model. */
  implicit def relLast[K, T](
      implicit t: RelType[T]
  ): Valid[Relation, FieldType[K, T] :: HNil] =
    new Valid[Relation, FieldType[K, T] :: HNil] {}

  /** Rule 16: one allowed attribute, plus a conformant remainder. */
  implicit def relMore[K, T, N <: HList](
      implicit t: RelType[T], n: Valid[Relation, N]
  ): Valid[Relation, FieldType[K, T] :: N] =
    new Valid[Relation, FieldType[K, T] :: N] {}

  // ---- Document model (rules 19 and 20) ------------------------------

  /** Rule 19. */
  implicit def docLast[K, T](
      implicit t: DocType[T]
  ): Valid[Document, FieldType[K, T] :: HNil] =
    new Valid[Document, FieldType[K, T] :: HNil] {}

  /** Rule 20. */
  implicit def docMore[K, T, N <: HList](
      implicit t: DocType[T], n: Valid[Document, N]
  ): Valid[Document, FieldType[K, T] :: N] =
    new Valid[Document, FieldType[K, T] :: N] {}
}

/**
 * Attribute domains allowed by the relational model.
 *
 * Encodes rules 17, 18 and the atomic types the article leaves implicit
 * behind an ellipsis. There is no instance for `List[_]` nor for `HList`,
 * which is what makes a nested or multi-valued attribute unrepresentable
 * in a relational schema.
 *
 * The following list of RelType is not intended to be exhaustive and can be easily extended.
 */
@implicitNotFound(
  "PolyFrames: ${T} is not an atomic domain, so it cannot be the domain of " +
  "an attribute of a relational schema."
)
trait RelType[T]

object RelType {
  private def evidence[T]: RelType[T] = new RelType[T] {}

  implicit val relString: RelType[String]   = evidence  // rule 17
  implicit val relInt: RelType[Int]         = evidence  // rule 18
  implicit val relLong: RelType[Long]       = evidence
  implicit val relDouble: RelType[Double]   = evidence
  implicit val relBoolean: RelType[Boolean] = evidence
}

/**
 * Attribute domains allowed by the document model.
 *
 * Encodes rules 21 to 24 and the atomic types left implicit by the article.
 * Two of the instances are recursive: `docList` accepts a multi-valued
 * domain whose element domain is itself allowed (rule 21), and `docNested`
 * accepts a nested schema that is itself conformant (rule 22). The whole
 * traversal of a nested schema is therefore performed by implicit
 * resolution alone.
 *
 * The following list of atomic DocType is not intended to be exhaustive and can be easily extended.
 */
@implicitNotFound(
  "PolyFrames: ${T} is not a domain allowed by the document model."
)
trait DocType[T]

object DocType {
  private def evidence[T]: DocType[T] = new DocType[T] {}

  implicit val docString: DocType[String]   = evidence  // rule 23
  implicit val docInt: DocType[Int]         = evidence  // rule 24
  implicit val docLong: DocType[Long]       = evidence
  implicit val docDouble: DocType[Double]   = evidence
  implicit val docBoolean: DocType[Boolean] = evidence

  /** Rule 21: multi-valued domain. */
  implicit def docList[T](implicit t: DocType[T]): DocType[List[T]] = evidence

  /** Rule 22: nested schema. */
  implicit def docNested[S <: HList](implicit v: Valid[Document, S]): DocType[S] = evidence
}
