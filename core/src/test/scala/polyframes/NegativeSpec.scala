package polyframes

import polyframes.infer._
import polyframes.ops.Ops._
import polyframes.runtime.Data
import polyframes.types._
import shapeless.{::, HNil}
import shapeless.labelled.FieldType
import shapeless.test.illTyped

/**
 * Compile-time checks for what must NOT compile.
 *
 * Every `illTyped` below is discharged by the compiler: the file compiles
 * only if each quoted expression fails to type-check. Compiling the test
 * sources (`sbt Test/compile`) is therefore enough to run these checks;
 * nothing has to be executed.
 */
object NegativeSpec {

  type SIdentity = FieldType["names", List[String]] :: FieldType["age", Int] :: HNil
  type SDoc      = FieldType["id", Int] :: FieldType["identity", SIdentity] :: HNil

  // ---- Conformance (rules 15 to 24) -----------------------------------

  illTyped("""implicitly[Valid[Relation, SIdentity]]""")       // multi-valued
  illTyped("""implicitly[Valid[Relation, SDoc]]""")            // nested
  illTyped("""implicitly[Valid[Document, HNil]]""")            // empty schema
  // ---- Reductions that must get stuck ---------------------------------

  illTyped("""implicitly[LookupFlat[SDoc, "identty"]]""")
  illTyped("""implicitly[AddFlat[SIdentity, "age", Int]]""")
  illTyped("""implicitly[Add[SDoc, "id" :: "x" :: HNil, Int]]""")
  illTyped("""implicitly[Add[SDoc, "identity" :: "age" :: HNil, Int]]""")
  illTyped("""implicitly[Merge[FieldType["a", Int] :: HNil, FieldType["a", String] :: HNil]]""")
  illTyped("""implicitly[Drop[SDoc, "price" :: HNil]]""")

  // ---- Operators ------------------------------------------------------
  // Only the types matter here: the operands are never evaluated.

  def doc: Data[Document, SDoc] = ???

  // Note. Attribute names are assumed unique (section 6): renaming onto an
  // existing name, or joining two sources that share a nested attribute
  // name, is not rejected, and fails at run time.

  // Announcing Relation for a nested result.
  illTyped("""project[Relation, ("identity" :: "age" :: HNil) :: HNil](doc)""")

  // ---- Collections ----------------------------------------------------
  // The type system follows a path through a collection wherever the
  // collection sits in the schema...
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
