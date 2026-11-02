package polyframes.infer

import shapeless.HList

/**
 * Evidence that a domain is a collection of nested schemas, exposing the
 * element schema as `Sub`.
 *
 * The rules that descend into a collection (rules 32 and 39, and their
 * counterparts in appendix B) require the head key of a path to resolve to
 * `List[Sub]`. Written as the premise `LookupFlat.Aux[S, K, List[Sub]]`,
 * that requirement asks the compiler to solve `Sub` while it is still
 * searching for the `LookupFlat` instance, which Scala 2 only manages when
 * the attribute sits at the head of the schema. Splitting it in two (look
 * the domain up first, then take it apart) lets the reduction succeed
 * wherever the attribute sits.
 */
trait ListOfSchema[T] { type Sub <: HList }

object ListOfSchema {
  type Aux[T, S <: HList] = ListOfSchema[T] { type Sub = S }

  implicit def onList[S <: HList]: Aux[List[S], S] =
    new ListOfSchema[List[S]] { type Sub = S }
}
