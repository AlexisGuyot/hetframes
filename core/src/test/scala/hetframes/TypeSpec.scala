package hetframes

import hetframes.infer._
import hetframes.types._
import shapeless.{::, HNil}
import shapeless.labelled.FieldType

/**
 * What must compile: conformance and every reduction, on the running
 * example of the article. Nothing runs; compiling the file is the test.
 *
 * Reductions are checked through `Aux`, so the expected schema is required,
 * not mere derivability (`Add[S, P, T]` alone would leave `Out` abstract).
 * What must not compile is in `NegativeSpec`.
 */
object TypeSpec {

  type SRel      = FieldType["id", Int] :: FieldType["name", String] :: HNil
  type SIdentity = FieldType["names", List[String]] :: FieldType["age", Int] :: HNil
  type SDoc      = FieldType["id", Int] :: FieldType["identity", SIdentity] :: HNil

  // ---- Conformance (rules 15 to 24) -----------------------------------

  implicitly[Valid[Relation, SRel]]
  implicitly[Valid[Document, SRel]]
  implicitly[Valid[Document, SDoc]]

  // ---- Lookup ---------------------------------------------------------

  implicitly[LookupFlat.Aux[SDoc, "identity", SIdentity]]
  implicitly[Lookup.Aux[SDoc, "identity" :: "age" :: HNil, Int]]

  // ---- Add (rules 35 to 38; Equations 61 and 62 of the article) -------

  type SIdentityPlus =
    FieldType["names", List[String]] :: FieldType["age", Int] ::
      FieldType["adult", Boolean] :: HNil
  type SDocPlus = FieldType["id", Int] :: FieldType["identity", SIdentityPlus] :: HNil

  implicitly[AddFlat.Aux[SIdentity, "adult", Boolean, SIdentityPlus]]
  implicitly[Add.Aux[SDoc, "identity" :: "adult" :: HNil, Boolean, SDocPlus]]

  // ---- Update ---------------------------------------------------------

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

  // ---- Project (rules 30 to 34) ---------------------------------------

  implicitly[ProjectOne.Aux[SDoc, "id" :: HNil, FieldType["id", Int] :: HNil]]

  // The enclosing nesting is rebuilt, not flattened.
  implicitly[ProjectOne.Aux[
    SDoc, "identity" :: "age" :: HNil,
    FieldType["identity", FieldType["age", Int] :: HNil] :: HNil
  ]]

  // Merge lays out the tail before the head: identity precedes id.
  implicitly[Project.Aux[
    SDoc, ("id" :: HNil) :: ("identity" :: "age" :: HNil) :: HNil,
    FieldType["identity", FieldType["age", Int] :: HNil] ::
      FieldType["id", Int] :: HNil
  ]]

  // ---- Merge: one nested attribute holding the union, not two ---------

  implicitly[Merge.Aux[
    FieldType["identity", FieldType["age", Int] :: HNil] :: HNil,
    FieldType["identity", FieldType["names", List[String]] :: HNil] :: HNil,
    FieldType["identity", SIdentity] :: HNil
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
}
