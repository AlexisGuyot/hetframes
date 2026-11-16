package hetframes.infer

import shapeless.{::, =:!=, HList, HNil}
import shapeless.labelled.FieldType

import scala.annotation.implicitNotFound

/**
 * Domain of the root attribute `K` of `S` (appendix B, `LookupFlat`).
 *
 * No rule for `HNil`: looking up a missing attribute gets stuck, which is
 * what makes it a compile-time error. Every `...Flat` type below follows
 * the same pattern.
 */
@implicitNotFound(
  "HetFrames: no attribute named ${K} at the root of the schema\n    ${S}"
)
trait LookupFlat[S <: HList, K] { type Out }

object LookupFlat {
  type Aux[S <: HList, K, O] = LookupFlat[S, K] { type Out = O }

  implicit def onHead[K, T, N <: HList]: Aux[FieldType[K, T] :: N, K, T] =
    new LookupFlat[FieldType[K, T] :: N, K] { type Out = T }

  implicit def onTail[K, K2, T2, N <: HList, O](
      implicit
      differ: K =:!= K2,
      next: Aux[N, K, O]
  ): Aux[FieldType[K2, T2] :: N, K, O] =
    new LookupFlat[FieldType[K2, T2] :: N, K] { type Out = O }
}

/**
 * Domain of the attribute at path `P` (appendix B, `Lookup`). A path is an
 * `HList` of singleton types: `K :: P` is the article's `Key(l) . P`.
 *
 * The two recursive rules share a conclusion and differ only in what the
 * head key leads to (a nested schema, or a collection of them). Scala
 * cannot choose between instances by their premises, so the collection
 * case sits in a low-priority trait and is tried second. `ProjectOne`,
 * `Add`, `Update`, `Drop` and `Rename` are built the same way.
 */
@implicitNotFound(
  "HetFrames: the path ${P} cannot be followed in the schema\n    ${S}"
)
trait Lookup[S <: HList, P <: HList] { type Out }

object Lookup extends LowPriorityLookup {
  type Aux[S <: HList, P <: HList, O] = Lookup[S, P] { type Out = O }

  implicit def onLast[S <: HList, K, T](
      implicit flat: LookupFlat.Aux[S, K, T]
  ): Aux[S, K :: HNil, T] =
    new Lookup[S, K :: HNil] { type Out = T }

  implicit def onNestedObject[S <: HList, K, P <: HList, Sub <: HList, T](
      implicit flat: LookupFlat.Aux[S, K, Sub],
      next: Aux[Sub, P, T]
  ): Aux[S, K :: P, T] =
    new Lookup[S, K :: P] { type Out = T }
}

trait LowPriorityLookup {

  implicit def onNestedList[S <: HList, K, P <: HList, L, Sub <: HList, T](
      implicit
      flat: LookupFlat.Aux[S, K, L],
      list: ListOfSchema.Aux[L, Sub],
      next: Lookup.Aux[Sub, P, T]
  ): Lookup.Aux[S, K :: P, T] =
    new Lookup[S, K :: P] { type Out = T }
}
