package hetframes.infer

import shapeless.{::, =:!=, HList, HNil}
import shapeless.labelled.FieldType

import scala.annotation.implicitNotFound

/**
 * Schema `S` with one attribute `K` of domain `T` inserted (appendix B,
 * `MergeOne`). A homonymous attribute is reconciled only when both domains
 * are nested schemas, or both collections of them; two atomic homonyms
 * have no rule, hence a compile-time error rather than a silent choice.
 */
@implicitNotFound(
  "HetFrames: cannot merge an attribute named ${K} of domain ${T} into the " +
  "schema\n    ${S}\n  The schema already holds an attribute of that name " +
  "whose domain cannot be reconciled with ${T}."
)
trait MergeOne[S <: HList, K, T] { type Out <: HList }

object MergeOne extends LowPriorityMergeOne {
  type Aux[S <: HList, K, T, O <: HList] = MergeOne[S, K, T] { type Out = O }

  implicit def onEmpty[K, T]: Aux[HNil, K, T, FieldType[K, T] :: HNil] =
    new MergeOne[HNil, K, T] { type Out = FieldType[K, T] :: HNil }

  implicit def onSameObject[K, Sub1 <: HList, Sub2 <: HList, N <: HList, NSub <: HList](
      implicit inner: Merge.Aux[Sub1, Sub2, NSub]
  ): Aux[FieldType[K, Sub2] :: N, K, Sub1, FieldType[K, NSub] :: N] =
    new MergeOne[FieldType[K, Sub2] :: N, K, Sub1] {
      type Out = FieldType[K, NSub] :: N
    }

  implicit def onSameList[K, Sub1 <: HList, Sub2 <: HList, N <: HList, NSub <: HList](
      implicit inner: Merge.Aux[Sub1, Sub2, NSub]
  ): Aux[FieldType[K, List[Sub2]] :: N, K, List[Sub1], FieldType[K, List[NSub]] :: N] =
    new MergeOne[FieldType[K, List[Sub2]] :: N, K, List[Sub1]] {
      type Out = FieldType[K, List[NSub]] :: N
    }
}

trait LowPriorityMergeOne {

  implicit def onOther[K, T, K2, T2, N <: HList, NN <: HList](
      implicit
      differ: K =:!= K2,
      next: MergeOne.Aux[N, K, T, NN]
  ): MergeOne.Aux[FieldType[K2, T2] :: N, K, T, FieldType[K2, T2] :: NN] =
    new MergeOne[FieldType[K2, T2] :: N, K, T] {
      type Out = FieldType[K2, T2] :: NN
    }
}

/**
 * Union of two schemas (appendix B, `Merge`): the attributes of `S2` come
 * first, those of `S1` are then inserted one by one with `MergeOne`.
 */
@implicitNotFound(
  "HetFrames: the schemas\n    ${S1}\n  and\n    ${S2}\n  cannot be merged."
)
trait Merge[S1 <: HList, S2 <: HList] { type Out <: HList }

object Merge {
  type Aux[S1 <: HList, S2 <: HList, O <: HList] =
    Merge[S1, S2] { type Out = O }

  implicit def onEmpty[S2 <: HList]: Aux[HNil, S2, S2] =
    new Merge[HNil, S2] { type Out = S2 }

  implicit def onOther[K, T, N <: HList, S2 <: HList, Tmp <: HList, NS <: HList](
      implicit
      one: MergeOne.Aux[S2, K, T, Tmp],
      next: Aux[N, Tmp, NS]
  ): Aux[FieldType[K, T] :: N, S2, NS] =
    new Merge[FieldType[K, T] :: N, S2] { type Out = NS }
}
