package hetframes.infer

import shapeless.{::, =:!=, HList, HNil}
import shapeless.labelled.FieldType

import scala.annotation.implicitNotFound

/**
 * Schema with the root attribute `K` given the domain `TNew`, name and
 * position unchanged (appendix B, `UpdateFlat`).
 */
@implicitNotFound(
  "HetFrames: no attribute named ${K} at the root of the schema\n    ${S}\n" +
  "  so its domain cannot be changed to ${TNew}."
)
trait UpdateFlat[S <: HList, K, TNew] { type Out <: HList }

object UpdateFlat {
  type Aux[S <: HList, K, TNew, O <: HList] =
    UpdateFlat[S, K, TNew] { type Out = O }

  implicit def onHead[K, TOld, TNew, N <: HList]
      : Aux[FieldType[K, TOld] :: N, K, TNew, FieldType[K, TNew] :: N] =
    new UpdateFlat[FieldType[K, TOld] :: N, K, TNew] {
      type Out = FieldType[K, TNew] :: N
    }

  implicit def onTail[K, TNew, K2, T2, N <: HList, NN <: HList](
      implicit
      differ: K =:!= K2,
      next: Aux[N, K, TNew, NN]
  ): Aux[FieldType[K2, T2] :: N, K, TNew, FieldType[K2, T2] :: NN] =
    new UpdateFlat[FieldType[K2, T2] :: N, K, TNew] {
      type Out = FieldType[K2, T2] :: NN
    }
}

/** Schema with the attribute at path `P` given the domain `TNew` (appendix B, `Update`). */
@implicitNotFound(
  "HetFrames: the path ${P} cannot be followed in the schema\n    ${S}"
)
trait Update[S <: HList, P <: HList, TNew] { type Out <: HList }

object Update extends LowPriorityUpdate {
  type Aux[S <: HList, P <: HList, TNew, O <: HList] =
    Update[S, P, TNew] { type Out = O }

  implicit def onLast[S <: HList, K, TNew, NS <: HList](
      implicit flat: UpdateFlat.Aux[S, K, TNew, NS]
  ): Aux[S, K :: HNil, TNew, NS] =
    new Update[S, K :: HNil, TNew] { type Out = NS }

  implicit def onNestedObject[
      S <: HList, K, P <: HList, TNew,
      Sub <: HList, NSub <: HList, NS <: HList
  ](
      implicit
      flat: LookupFlat.Aux[S, K, Sub],
      inner: Aux[Sub, P, TNew, NSub],
      back: UpdateFlat.Aux[S, K, NSub, NS]
  ): Aux[S, K :: P, TNew, NS] =
    new Update[S, K :: P, TNew] { type Out = NS }
}

trait LowPriorityUpdate {

  implicit def onNestedList[
      S <: HList, K, P <: HList, TNew,
      L, Sub <: HList, NSub <: HList, NS <: HList
  ](
      implicit
      flat: LookupFlat.Aux[S, K, L],
      list: ListOfSchema.Aux[L, Sub],
      inner: Update.Aux[Sub, P, TNew, NSub],
      back: UpdateFlat.Aux[S, K, List[NSub], NS]
  ): Update.Aux[S, K :: P, TNew, NS] =
    new Update[S, K :: P, TNew] { type Out = NS }
}
