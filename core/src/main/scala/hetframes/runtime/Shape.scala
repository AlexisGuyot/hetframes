package hetframes.runtime

import org.apache.spark.sql.Column
import org.apache.spark.sql.functions.struct
import shapeless.labelled.FieldType
import shapeless.{::, HList, HNil, Witness}

import scala.annotation.implicitNotFound

/**
 * The columns a schema type designates, in schema order.
 *
 * The factory of `Data` selects them after every operator, so the column
 * order of a DataFrame is always the attribute order of its type: the row
 * decoder can work by position, and projection and removal need no code of
 * their own. `get` resolves a name at the current level of nesting.
 * Collections of nested schemas are read as is, not rebuilt.
 */
@implicitNotFound("HetFrames: cannot lay out the columns of the schema\n    ${S}")
trait Shape[S <: HList] { def columns(get: String => Column): List[Column] }

object Shape extends LowPriorityShape {
  def apply[S <: HList](implicit shape: Shape[S]): Shape[S] = shape

  implicit val empty: Shape[HNil] = new Shape[HNil] {
    def columns(get: String => Column): List[Column] = Nil
  }

  /** A nested schema is rebuilt as a struct of its own columns. */
  implicit def nested[K <: String, Sub <: HList, N <: HList](
      implicit
      key: Witness.Aux[K],
      inner: Shape[Sub],
      tail: Shape[N]
  ): Shape[FieldType[K, Sub] :: N] = new Shape[FieldType[K, Sub] :: N] {
    def columns(get: String => Column): List[Column] = {
      val here = get(key.value)
      struct(inner.columns(name => here.getField(name)): _*)
        .as(key.value) :: tail.columns(get)
    }
  }
}

trait LowPriorityShape {

  /** Any other domain is read as is. */
  implicit def atomic[K <: String, T, N <: HList](
      implicit key: Witness.Aux[K],
      tail: Shape[N]
  ): Shape[FieldType[K, T] :: N] = new Shape[FieldType[K, T] :: N] {
    def columns(get: String => Column): List[Column] =
      get(key.value).as(key.value) :: tail.columns(get)
  }
}
