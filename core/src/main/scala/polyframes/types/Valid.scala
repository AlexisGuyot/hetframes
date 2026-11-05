package polyframes.types

import shapeless.{::, HList, HNil}
import shapeless.labelled.FieldType

import scala.annotation.implicitNotFound

/**
 * Conformance of a schema to a model (rules 15, 16, 19 and 20).
 *
 * No instance for `HNil`: every rule requires at least one attribute. The
 * one-attribute cases are more specific than the recursive ones, so the
 * compiler picks them without ambiguity.
 */
@implicitNotFound(
  "PolyFrames: the schema\n    ${S}\n  does not conform to the model ${M}. " +
  "Check the domain of every attribute, and, for the relational model, that " +
  "no attribute is nested or multi-valued."
)
trait Valid[M <: Model, S <: HList]

object Valid {

  /** Rule 15. */
  implicit def relLast[K, T](
      implicit t: RelType[T]
  ): Valid[Relation, FieldType[K, T] :: HNil] =
    new Valid[Relation, FieldType[K, T] :: HNil] {}

  /** Rule 16. */
  implicit def relMore[K, T, N <: HList](
      implicit t: RelType[T], n: Valid[Relation, N]
  ): Valid[Relation, FieldType[K, T] :: N] =
    new Valid[Relation, FieldType[K, T] :: N] {}

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
 * Domains of the relational model (rules 17 and 18, plus the atomic types
 * the article leaves behind an ellipsis; the list can be extended). No
 * instance for `List[_]` nor `HList`: that is what rejects nested and
 * multi-valued attributes.
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

/** Domains of the document model (rules 21 to 24, extensible likewise). */
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

  /** Rule 21. */
  implicit def docList[T](implicit t: DocType[T]): DocType[List[T]] = evidence

  /** Rule 22. */
  implicit def docNested[S <: HList](implicit v: Valid[Document, S]): DocType[S] = evidence
}
