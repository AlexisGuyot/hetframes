package hetframes.variants

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import hetframes.bench.{P1DataFrame, Sink}

/**
 * DataFrames, an attribute of an unexpected domain.
 *
 * Same injection as hf-badtype and ds-badtype: carb_share is created as a
 * string at step 1 and read as a number at step 4.
 *
 * Expected: compiles; fails when the query is analysed, at run time.
 */
object DfBadType {

  def main(args: Array[String]): Unit = {
    val path = args.headOption.getOrElse("data/products-10000.jsonl")
    val spark = SparkSession.builder().master("local[*]").appName("DfBadType").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val d0 = spark.read.schema(P1DataFrame.ReadSchema).json(path)

    // 1. The mistake: the share is created as a label, not as a fraction.
    val d1 = d0.withColumn("nutriments", col("nutriments").withField("carb_share", lit("high")))
    val d2 = d1.withColumn("is_beverage", coalesce(col("pnns_groups_1") === "Beverages", lit(false)))
    val d3 = d2.select(col("code"), col("nutriscore_grade"), col("is_beverage"),
      struct(col("nutriments.carb_share").as("carb_share")).as("nutriments"))

    // 4. Read as a number, two operators after it was created.
    val d4 = d3.withColumn("dense", col("nutriments.carb_share") > 0.5)
    val d5 = d4.select("code", "nutriscore_grade", "is_beverage", "dense")
    val d6 = d5.withColumn("flag", col("dense") && !col("is_beverage"))
    Sink.untyped(d6, Sink.scratch(spark, "DfBadType"))

    spark.stop()
  }
}
