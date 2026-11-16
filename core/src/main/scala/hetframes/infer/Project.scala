package hetframes.infer

import shapeless.{::, HList, HNil}
import shapeless.labelled.FieldType

import scala.annotation.implicitNotFound

/**
 * Schema holding the single attribute at path `P`, with the nesting that
 * encloses it rebuilt rather than flattened (rules 30 to 32).
 */
@implicitNotFound(
  "HetFrames: the path ${P} cannot be projected from the schema\n    ${S}"
)
trait ProjectOne[S <: HList, P <: HList] { type Out <: HList }

object ProjectOne extends LowPriorityProjectOne {
  type Aux[S <: HList, P <: HList, O <: HList] =
    ProjectOne[S, P] { type Out = O }

  /** Rule 30. */
  implicit def onLast[S <: HList, K, T](
      implicit flat: LookupFlat.Aux[S, K, T]
  ): Aux[S, K :: HNil, FieldType[K, T] :: HNil] =
    new ProjectOne[S, K :: HNil] { type Out = FieldType[K, T] :: HNil }

  /** Rule 31. */
  implicit def onNestedObject[S <: HList, K, P <: HList, Sub <: HList, NSub <: HList](
      implicit
      flat: LookupFlat.Aux[S, K, Sub],
      inner: Aux[Sub, P, NSub]
  ): Aux[S, K :: P, FieldType[K, NSub] :: HNil] =
    new ProjectOne[S, K :: P] { type Out = FieldType[K, NSub] :: HNil }
}

trait LowPriorityProjectOne {

  /** Rule 32. */
  implicit def onNestedList[S <: HList, K, P <: HList, L, Sub <: HList, NSub <: HList](
      implicit
      flat: LookupFlat.Aux[S, K, L],
      list: ListOfSchema.Aux[L, Sub],
      inner: ProjectOne.Aux[Sub, P, NSub]
  ): ProjectOne.Aux[S, K :: P, FieldType[K, List[NSub]] :: HNil] =
    new ProjectOne[S, K :: P] { type Out = FieldType[K, List[NSub]] :: HNil }
}

/**
 * Schema holding the attributes at a list of paths (rules 33 and 34). Each
 * path is projected alone and the results are combined with `Merge`, so
 * two paths sharing a prefix yield one nested attribute, not two.
 */
@implicitNotFound(
  "HetFrames: the paths ${L} cannot be projected from the schema\n    ${S}"
)
trait Project[S <: HList, L <: HList] { type Out <: HList }

object Project {
  type Aux[S <: HList, L <: HList, O <: HList] =
    Project[S, L] { type Out = O }

  /** Rule 33. */
  implicit def onEmpty[S <: HList]: Aux[S, HNil, HNil] =
    new Project[S, HNil] { type Out = HNil }

  /** Rule 34. */
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
