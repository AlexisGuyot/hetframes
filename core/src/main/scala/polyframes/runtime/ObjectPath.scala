package polyframes.runtime

import polyframes.infer.LookupFlat
import shapeless.{::, HList, HNil}

import scala.annotation.implicitNotFound

/**
 * Evidence that the path `P` reaches its target through nested schemas
 * only. No counterpart in the article, whose rules admit paths through
 * collections (rules 32, 39 and appendix B): the binding to SparkSQL does
 * not yet rebuild arrays of structs, so such a path is rejected at compile
 * time rather than executed unfaithfully.
 */
@implicitNotFound(
  "PolyFrames: the path ${P} traverses a collection in the schema\n    ${S}\n" +
  "  The type system admits it, but the binding to SparkSQL does not yet " +
  "support operators acting inside collections."
)
trait ObjectPath[S <: HList, P <: HList]

object ObjectPath {
  implicit def onLast[S <: HList, K]: ObjectPath[S, K :: HNil] =
    new ObjectPath[S, K :: HNil] {}

  /** An intermediate key must lead to a nested schema. */
  implicit def onNested[S <: HList, K, P <: HList, Sub <: HList](
      implicit
      flat: LookupFlat.Aux[S, K, Sub],
      next: ObjectPath[Sub, P]
  ): ObjectPath[S, K :: P] =
    new ObjectPath[S, K :: P] {}
}

/** `ObjectPath` for every path of a list, for the projection. */
@implicitNotFound(
  "PolyFrames: one of the paths ${L} traverses a collection in the schema\n    ${S}\n" +
  "  The binding to SparkSQL does not yet support operators acting inside collections."
)
trait ObjectPaths[S <: HList, L <: HList]

object ObjectPaths {
  implicit def onEmpty[S <: HList]: ObjectPaths[S, HNil] = new ObjectPaths[S, HNil] {}

  implicit def onOther[S <: HList, P <: HList, OP <: HList](
      implicit head: ObjectPath[S, P], tail: ObjectPaths[S, OP]
  ): ObjectPaths[S, P :: OP] = new ObjectPaths[S, P :: OP] {}
}
