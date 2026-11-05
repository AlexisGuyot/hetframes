package polyframes.runtime

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions.col
import polyframes.types.{Model, Valid}
import shapeless.HList

/**
 * The typed layer over a DataFrame (rule 9), and the only inhabited type of
 * the system. The constructor is private: the factory, which requires
 * `Valid[M, S]`, is the only way to obtain a value, so no `Data[M, S]` can
 * exist whose schema has not been checked against its model.
 */
final class Data[M <: Model, S <: HList] private (
    private[polyframes] val df: DataFrame
)

object Data {

  /**
   * Rule 9: the subtyping premises are the type bounds, the conformance
   * premise is `valid`. `shape` belongs to the binding to Spark: it lays the
   * columns out in schema order (see `Shape`).
   */
  def apply[M <: Model, S <: HList](df: DataFrame)(
      implicit
      valid: Valid[M, S],
      shape: Shape[S]
  ): Data[M, S] =
    new Data[M, S](df.select(shape.columns(name => col(s"`$name`")): _*))
}
