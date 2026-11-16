package hetframes.infer

import shapeless.{::, =:!=, HList, HNil}
import shapeless.labelled.FieldType

import scala.annotation.implicitNotFound

/** Schema without its root attribute `K` (appendix B, `DropFlat`). */
@implicitNotFound(
  "HetFrames: no attribute named ${K} at the root of the schema\n    ${S}\n" +
  "  so it cannot be removed."
)
trait DropFlat[S <: HList, K] { type Out <: HList }

object DropFlat {
  type Aux[S <: HList, K, O <: HList] = DropFlat[S, K] { type Out = O }

  implicit def onHead[K, T, N <: HList]: Aux[FieldType[K, T] :: N, K, N] =
    new DropFlat[FieldType[K, T] :: N, K] { type Out = N }

  implicit def onTail[K, K2, T2, N <: HList, NN <: HList](
      implicit
      differ: K =:!= K2,
      next: Aux[N, K, NN]
  ): Aux[FieldType[K2, T2] :: N, K, FieldType[K2, T2] :: NN] =
    new DropFlat[FieldType[K2, T2] :: N, K] {
      type Out = FieldType[K2, T2] :: NN
    }
}

/** Schema without the attribute at path `P` (appendix B, `Drop`). */
@implicitNotFound(
  "HetFrames: the path ${P} cannot be followed in the schema\n    ${S}"
)
trait Drop[S <: HList, P <: HList] { type Out <: HList }

object Drop extends LowPriorityDrop {
  type Aux[S <: HList, P <: HList, O <: HList] = Drop[S, P] { type Out = O }

  implicit def onLast[S <: HList, K, NS <: HList](
      implicit
      flat: DropFlat.Aux[S, K, NS]
  ): Aux[S, K :: HNil, NS] =
    new Drop[S, K :: HNil] { type Out = NS }

  implicit def onNestedObject[
      S <: HList, K, P <: HList, Sub <: HList, NSub <: HList, NS <: HList
  ](
      implicit
      flat: LookupFlat.Aux[S, K, Sub],
      inner: Aux[Sub, P, NSub],
      back: UpdateFlat.Aux[S, K, NSub, NS]
  ): Aux[S, K :: P, NS] =
    new Drop[S, K :: P] { type Out = NS }
}

trait LowPriorityDrop {
  implicit def onNestedList[
      S <: HList, K, P <: HList, L, Sub <: HList, NSub <: HList, NS <: HList
  ](
      implicit
      flat: LookupFlat.Aux[S, K, L],
      list: ListOfSchema.Aux[L, Sub],
      inner: Drop.Aux[Sub, P, NSub],
      back: UpdateFlat.Aux[S, K, List[NSub], NS]
  ): Drop.Aux[S, K :: P, NS] =
    new Drop[S, K :: P] { type Out = NS }
}
