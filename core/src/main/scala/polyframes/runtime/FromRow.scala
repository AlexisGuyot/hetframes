package polyframes.runtime

import org.apache.spark.sql.Row
import shapeless.labelled.{FieldType, field}
import shapeless.{::, HList, HNil}

import scala.annotation.implicitNotFound

/**
 * Decoder for one attribute domain. The instances mirror the domains the
 * document model admits, so a domain the type system accepts is always
 * decodable.
 *
 * A missing value decodes to the zero of its domain, a missing collection
 * to `Nil`: the type system has no optional attributes, and this states
 * that simplification in one place rather than leaving it to unboxing.
 */
@implicitNotFound("PolyFrames: no way to decode a value of domain ${T}.")
trait FromCell[T] extends Serializable { def apply(value: Any): T }

object FromCell {

  private def cast[T]: FromCell[T] = new FromCell[T] {
    def apply(value: Any): T = value.asInstanceOf[T]
  }

  private def orElse[T](default: T): FromCell[T] = new FromCell[T] {
    def apply(value: Any): T =
      if (value == null) default else value.asInstanceOf[T]
  }

  implicit val cellString: FromCell[String]   = cast
  implicit val cellInt: FromCell[Int]         = orElse(0)
  implicit val cellLong: FromCell[Long]       = orElse(0L)
  implicit val cellDouble: FromCell[Double]   = orElse(0.0)
  implicit val cellBoolean: FromCell[Boolean] = orElse(false)

  implicit def cellList[T](implicit element: FromCell[T]): FromCell[List[T]] =
    new FromCell[List[T]] {
      def apply(value: Any): List[T] = value match {
        case null                      => Nil
        case s: scala.collection.Seq[_] => s.iterator.map(element.apply).toList
        case a: Array[_]               => a.iterator.map(element.apply).toList
        case other =>
          sys.error(s"PolyFrames: expected a collection, found ${other.getClass}")
      }
    }

  implicit def cellNested[S <: HList](implicit inner: FromRow[S]): FromCell[S] =
    new FromCell[S] {
      def apply(value: Any): S = inner(value.asInstanceOf[Row], 0)
    }
}

/**
 * Decoder from a row to a term of the schema type `S`, which gives the
 * function of `add` and `update` its type `S => T` (rule 41).
 *
 * Decoding is positional: the operators hand it a row built in schema
 * order. With `FromCell`, it is the only serialisable part of the library,
 * since it is shipped to the executors and runs once per record; it is
 * therefore the whole run-time cost of the type system.
 */
@implicitNotFound("PolyFrames: no way to decode a row into the schema\n    ${S}")
trait FromRow[S <: HList] extends Serializable { def apply(row: Row, from: Int): S }

object FromRow {

  implicit val empty: FromRow[HNil] = new FromRow[HNil] {
    def apply(row: Row, from: Int): HNil = HNil
  }

  implicit def attribute[K, T, N <: HList](
      implicit head: FromCell[T],
      tail: FromRow[N]
  ): FromRow[FieldType[K, T] :: N] = new FromRow[FieldType[K, T] :: N] {
    def apply(row: Row, from: Int): FieldType[K, T] :: N =
      field[K](head(row.get(from))) :: tail(row, from + 1)
  }
}
