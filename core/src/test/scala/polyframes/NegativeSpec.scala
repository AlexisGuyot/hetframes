package polyframes

import polyframes.infer._
import polyframes.ops.Ops._
import polyframes.runtime.{Data, FromCell}
import polyframes.types._
import shapeless.{::, HNil}
import shapeless.labelled.FieldType
import shapeless.test.illTyped

/**
 * What must not compile. The file compiles only if every `illTyped`
 * expression fails to type-check, so `sbt Test/compile` runs these checks.
 */
object NegativeSpec {

  type SIdentity = FieldType["names", List[String]] :: FieldType["age", Int] :: HNil
  type SDoc      = FieldType["id", Int] :: FieldType["identity", SIdentity] :: HNil

  // ---- Conformance (rules 15 to 24) -----------------------------------

  illTyped("""implicitly[Valid[Relation, SIdentity]]""")       // multi-valued
  illTyped("""implicitly[Valid[Relation, SDoc]]""")            // nested
  illTyped("""implicitly[Valid[Document, HNil]]""")            // empty schema

  // ---- Reductions that must get stuck ---------------------------------

  illTyped("""implicitly[LookupFlat[SDoc, "identty"]]""")      // missing attribute
  illTyped("""implicitly[AddFlat[SIdentity, "age", Int]]""")   // already present
  illTyped("""implicitly[Add[SDoc, "id" :: "x" :: HNil, Int]]""")             // through an atomic domain
  illTyped("""implicitly[Add[SDoc, "identity" :: "age" :: HNil, Int]]""")     // already present, nested
  illTyped("""implicitly[Merge[FieldType["a", Int] :: HNil, FieldType["a", String] :: HNil]]""")
  illTyped("""implicitly[Drop[SDoc, "price" :: HNil]]""")

  // ---- Runtime layer --------------------------------------------------

  illTyped("""implicitly[FromCell[java.net.URL]]""")           // not a domain of the system

  // ---- Operators (only the types matter: operands are never evaluated) -

  def doc: Data[Document, SDoc] = ???

  // Announcing Relation for a nested result.
  illTyped("""project[Relation, ("identity" :: "age" :: HNil) :: HNil](doc)""")

  // Not covered (section 6.2): attribute names are assumed unique, so a
  // renaming onto an existing name, or a join of two sources sharing a
  // nested attribute name, type-checks and fails at run time.

  // ---- Collections ----------------------------------------------------
  // The rules follow a path through a collection wherever it sits...
  type Item  = FieldType["sku", String] :: FieldType["qty", Int] :: HNil
  type SList = FieldType["id", Int] :: FieldType["items", List[Item]] :: HNil
  implicitly[Lookup.Aux[SList, "items" :: "qty" :: HNil, Int]]
  implicitly[ProjectOne.Aux[SList, "items" :: "qty" :: HNil,
    FieldType["items", List[FieldType["qty", Int] :: HNil]] :: HNil]]

  // ...but the binding to SparkSQL refuses operators acting inside one.
  def withList: Data[Document, SList] = ???
  illTyped("""project[Document, ("items" :: "qty" :: HNil) :: HNil](withList)""", ".*traverses a collection.*")
  illTyped("""drop[Document, "items" :: "qty" :: HNil](withList)""", ".*traverses a collection.*")

  // Keeping a whole collection is fine.
  def keepWhole = project[Document, ("items" :: HNil) :: HNil](withList)
}
