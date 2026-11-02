package polyframes.runtime

import polyframes.infer.LookupFlat
import shapeless.{::, HList, HNil}

import scala.annotation.implicitNotFound

/**
 * Evidence that the path `P` reaches its target in `S` through nested
 * schemas only, without traversing a collection.
 *
 * This has no counterpart in the article's type system, which admits paths
 * through collections of nested schemas (rules 32, 39 and their analogues
 * in appendix B). It belongs to the binding to SparkSQL, which does not
 * yet rebuild arrays of structs: an operator acting inside a collection
 * would type-check but could not be executed faithfully. Requiring this
 * evidence turns that gap into a compile-time error instead of a run-time
 * failure or, worse, a DataFrame whose layout disagrees with its type.
 */
@implicitNotFound(
  "PolyFrames: the path ${P} traverses a collection in the schema\n    ${S}\n" +
  "  The type system admits it, but the binding to SparkSQL does not yet " +
  "support operators acting inside collections."
)
trait ObjectPath[S <: HList, P <: HList]

object ObjectPath {
  /** The last key: nothing left to traverse. */
  implicit def onLast[S <: HList, K]: ObjectPath[S, K :: HNil] =
    new ObjectPath[S, K :: HNil] {}

  /** An intermediate key must lead to a nested schema, not a collection. */
  implicit def onNested[S <: HList, K, P <: HList, Sub <: HList](
      implicit
      flat: LookupFlat.Aux[S, K, Sub],
      next: ObjectPath[Sub, P]
  ): ObjectPath[S, K :: P] =
    new ObjectPath[S, K :: P] {}
}

/** `ObjectPath` lifted to a list of paths, for the projection. */
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
