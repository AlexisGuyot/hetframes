package hetframes.ops

import org.apache.spark.sql.api.java.UDF1
import org.apache.spark.sql.functions.{col, expr}
import org.apache.spark.sql.{Column, Row}
import hetframes.infer._
import hetframes.runtime._
import hetframes.types.{Model, Valid}
import shapeless.{HList, Witness}

import java.util.concurrent.atomic.AtomicLong

/**
 * The operators of the article (rules 40 and 41, appendix C).
 *
 * Each is applied in two stages, `add[Document, P](d)(f)`: the output model
 * and the paths are supplied by hand, while the schemas and the inferred
 * output schema must be inferred, and Scala 2 cannot mix the two in one
 * application. Every operator ends with the factory of `Data`, which checks
 * the output schema against the announced model and reorders the columns,
 * which is all projection and removal need.
 *
 * The `reachable` premises have no counterpart in the rules: see
 * `ObjectPath`.
 */
object Ops {

  private val udfCounter = new AtomicLong(0L)

  /** Rule 40. */
  def project[MO <: Model, L <: HList]: ProjectOp[MO, L] = new ProjectOp[MO, L]

  /** Rule 41. */
  def add[MO <: Model, P <: HList]: AddOp[MO, P] = new AddOp[MO, P]

  /** Appendix C, by analogy with rule 41. */
  def update[MO <: Model, P <: HList]: UpdateOp[MO, P] = new UpdateOp[MO, P]

  /** Appendix C, by analogy with rule 40. */
  def drop[MO <: Model, P <: HList]: DropOp[MO, P] = new DropOp[MO, P]

  /** Appendix C. */
  def rename[MO <: Model, P <: HList, KNew <: String]: RenameOp[MO, P, KNew] =
    new RenameOp[MO, P, KNew]

  /** Appendix C. The only binary operator. */
  def join[MO <: Model, PL <: HList, PR <: HList]: JoinOp[MO, PL, PR] =
    new JoinOp[MO, PL, PR]

  // -------------------------------------------------------------------

  final class ProjectOp[MO <: Model, L <: HList] {
    def apply[M <: Model, S <: HList, NS <: HList](d: Data[M, S])(
        implicit
        reduce: Project.Aux[S, L, NS],
        valid: Valid[MO, NS],
        shape: Shape[NS],
        reachable: ObjectPaths[S, L]
    ): Data[MO, NS] = Data[MO, NS](d.df)
  }

  final class DropOp[MO <: Model, P <: HList] {
    def apply[M <: Model, S <: HList, NS <: HList](d: Data[M, S])(
        implicit
        reduce: Drop.Aux[S, P, NS],
        valid: Valid[MO, NS],
        shape: Shape[NS],
        reachable: ObjectPath[S, P]
    ): Data[MO, NS] = Data[MO, NS](d.df)
  }

  final class AddOp[MO <: Model, P <: HList] {
    // Two parameter lists, so that `S` is fixed before `f` is typed and
    // its parameter needs no annotation.
    def apply[M <: Model, S <: HList](d: Data[M, S]) = new Applied[M, S](d)

    final class Applied[M <: Model, S <: HList](d: Data[M, S]) {
      def apply[T, NS <: HList](f: S => T)(
        implicit
        reduce: Add.Aux[S, P, T, NS],
        valid: Valid[MO, NS],
        shape: Shape[NS],
        reachable: ObjectPath[S, P],
        decode: FromRow[S],
        target: Target[P],
        domain: SparkType[T]
      ): Data[MO, NS] =
        Data[MO, NS](target.insert(d.df, record(d, f, domain)))
    }
  }

  final class UpdateOp[MO <: Model, P <: HList] {
    def apply[M <: Model, S <: HList](d: Data[M, S]) = new Applied[M, S](d)

    final class Applied[M <: Model, S <: HList](d: Data[M, S]) {
      def apply[T, NS <: HList](f: S => T)(
        implicit
        reduce: Update.Aux[S, P, T, NS],
        valid: Valid[MO, NS],
        shape: Shape[NS],
        reachable: ObjectPath[S, P],
        decode: FromRow[S],
        target: Target[P],
        domain: SparkType[T]
      ): Data[MO, NS] =
        Data[MO, NS](target.insert(d.df, record(d, f, domain)))
    }
  }

  final class RenameOp[MO <: Model, P <: HList, KNew <: String] {
    def apply[M <: Model, S <: HList, NS <: HList](d: Data[M, S])(
        implicit
        reduce: Rename.Aux[S, P, KNew, NS],
        valid: Valid[MO, NS],
        shape: Shape[NS],
        reachable: ObjectPath[S, P],
        target: Target[P],
        name: Witness.Aux[KNew]
    ): Data[MO, NS] =
      Data[MO, NS](target.renameTo(d.df, name.value))
  }

  /**
   * Inner join on one attribute of each operand, of the same domain.
   * `Merge` rejects two operands sharing an atomic attribute name. Known
   * limitation: a shared nested attribute name type-checks, but the two
   * struct columns are not merged and the result fails at run time.
   */
  final class JoinOp[MO <: Model, PL <: HList, PR <: HList] {
    def apply[
        ML <: Model, MR <: Model, SL <: HList, SR <: HList,
        TL, TR, NS <: HList
    ](left: Data[ML, SL], right: Data[MR, SR])(
        implicit
        keyLeft: Lookup.Aux[SL, PL, TL],
        keyRight: Lookup.Aux[SR, PR, TR],
        sameDomain: TL =:= TR,
        merge: Merge.Aux[SL, SR, NS],
        valid: Valid[MO, NS],
        shape: Shape[NS],
        reachableLeft: ObjectPath[SL, PL],
        reachableRight: ObjectPath[SR, PR],
        targetLeft: Target[PL],
        targetRight: Target[PR]
    ): Data[MO, NS] =
      Data[MO, NS](
        left.df.join(
          right.df,
          col(targetLeft.dotted) === col(targetRight.dotted),
          "inner"
        )
      )
  }

  // -------------------------------------------------------------------

  /**
   * Turns `f` into a column: a UDF applied to `struct(*)`, which is a term
   * of the schema type since the columns are in schema order. Both `f` and
   * the decoder are shipped to the executors, so both must be serialisable.
   */
  private def record[M <: Model, S <: HList, T](
      d: Data[M, S],
      f: S => T,
      domain: SparkType[T]
  )(implicit decode: FromRow[S]): Column = {
    val name = "hetframes_udf_" + udfCounter.incrementAndGet()
    d.df.sparkSession.udf.register(
      name,
      new UDF1[Row, T] { def call(row: Row): T = f(decode(row, 0)) },
      domain.dataType
    )
    expr(s"$name(struct(*))")
  }
}
