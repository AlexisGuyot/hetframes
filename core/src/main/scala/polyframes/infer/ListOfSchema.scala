package polyframes.infer

import shapeless.HList

/**
 * Evidence that a domain is `List[Sub]`, for the rules descending into a
 * collection (rules 32 and 39, and their counterparts in appendix B).
 *
 * The premise is split in two (look the domain up, then take it apart)
 * because Scala 2 cannot solve `Sub` inside `LookupFlat.Aux[S, K, List[Sub]]`
 * unless the attribute is the head of the schema.
 */
trait ListOfSchema[T] { type Sub <: HList }

object ListOfSchema {
  type Aux[T, S <: HList] = ListOfSchema[T] { type Sub = S }

  implicit def onList[S <: HList]: Aux[List[S], S] =
    new ListOfSchema[List[S]] { type Sub = S }
}
