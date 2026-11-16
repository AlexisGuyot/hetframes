package hetframes.bench

import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
import hetframes.runtime.Data
import hetframes.types.Relation
import shapeless.HList

/**
 * The CSV sink every pipeline of section 5 ends on (appendix C, `writeCSV`).
 * CSV only carries flat data: the typed version requires `Relation` at
 * compile time, the other two leave Spark to fail when the file is written.
 */
object Sink {

  /** Only relational data can be written. Checked by the compiler. */
  def typed[S <: HList](d: Data[Relation, S], path: String): Unit =
    d.df.write.mode("overwrite").csv(path)

  /** Anything is accepted. Checked, if at all, at run time. */
  def untyped(df: DataFrame, path: String): Unit =
    df.write.mode("overwrite").csv(path)

  /** Likewise: a Dataset does not record the model of its data. */
  def ofDataset[T](ds: Dataset[T], path: String): Unit =
    ds.write.mode("overwrite").csv(path)

  /** A directory of its own for each write. See Workspace.out. */
  def scratch(spark: SparkSession, name: String): String = Workspace.out(name)
}
