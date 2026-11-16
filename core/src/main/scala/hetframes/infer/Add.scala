package hetframes.infer

import shapeless.{::, =:!=, HList, HNil}
import shapeless.labelled.FieldType

import scala.annotation.implicitNotFound

/**
 * Schema with an attribute `K` of domain `T` appended at the root (rules 35
 * and 36).
 *
 * The `=:!=` witness is the side condition l != l' of rule 36: once the
 * name is found, no instance applies, so a duplicate is never appended.
 */
@implicitNotFound(
  "HetFrames: cannot add an attribute named ${K} to the schema\n    ${S}\n" +
  "  Either the schema already holds an attribute of that name, or the " +
  "path leading to it cannot be followed."
)
trait AddFlat[S <: HList, K, T] { type Out <: HList }

object AddFlat {
  type Aux[S <: HList, K, T, O <: HList] = AddFlat[S, K, T] { type Out = O }

  /** Rule 35. */
  implicit def onEmpty[K, T]: Aux[HNil, K, T, FieldType[K, T] :: HNil] =
    new AddFlat[HNil, K, T] { type Out = FieldType[K, T] :: HNil }

  /** Rule 36. */
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
 * Schema with the attribute at path `P` created with domain `T` (rules 37
 * to 39): descend with `LookupFlat`, add, write back with `UpdateFlat`.
 */
@implicitNotFound(
  "HetFrames: cannot add the attribute at path ${P} of domain ${T} to the " +
  "schema\n    ${S}"
)
trait Add[S <: HList, P <: HList, T] { type Out <: HList }

object Add extends LowPriorityAdd {
  type Aux[S <: HList, P <: HList, T, O <: HList] =
    Add[S, P, T] { type Out = O }

  /** Rule 37. */
  implicit def onLast[S <: HList, K, T, NS <: HList](
      implicit
      flat: AddFlat.Aux[S, K, T, NS]
  ): Aux[S, K :: HNil, T, NS] =
    new Add[S, K :: HNil, T] { type Out = NS }

  /** Rule 38. */
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

  /** Rule 39. */
  implicit def onNestedList[
      S <: HList, K, P <: HList, T,
      L, Sub <: HList, NSub <: HList, NS <: HList
  ](
      implicit
      flat: LookupFlat.Aux[S, K, L],
      list: ListOfSchema.Aux[L, Sub],
      inner: Add.Aux[Sub, P, T, NSub],
      back: UpdateFlat.Aux[S, K, List[NSub], NS]
  ): Add.Aux[S, K :: P, T, NS] =
    new Add[S, K :: P, T] { type Out = NS }
}
