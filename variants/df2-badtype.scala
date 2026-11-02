package polyframes.variants

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import polyframes.bench.{P1DataFrame, P2DataFrame, Sink}

/**
 * DataFrames, an attribute of an unexpected domain.
 *
 * Same injection as pf2-badtype and ds2-badtype: grade_rank is updated to
 * a string at step 4 and read as a number at step 6.
 *
 * Expected: compiles; fails at run time, when rows are written.
 */
object Df2BadType {

  def main(args: Array[String]): Unit = {
    val products = args.headOption.getOrElse("data/products-10000.jsonl")
    val grades = args.drop(1).headOption.getOrElse("reference/nutriscore-grades.csv")
    val spark = SparkSession.builder().master("local[*]").appName("Df2BadType").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val energy = col("nutriments.`energy-kcal_100g`")
    val carbs  = col("nutriments.`carbohydrates_100g`")

    val p = spark.read.schema(P1DataFrame.ReadSchema).json(products)
    val g = spark.read.schema(P2DataFrame.GradeSchema).option("header", "true").csv(grades)

    val d1 = p.join(g, col("nutriscore_grade") === col("grade_code"), "inner")
    val d2 = d1.withColumn("nutriments", col("nutriments").dropFields("salt"))
    val d3 = d2.withColumnRenamed("code", "barcode")
    // 4. The mistake: the rank is replaced with a label, not a fraction.
    val d4 = d3.withColumn("grade_rank", lit("high"))
    val d5 = d4.withColumn("nutriments", col("nutriments").withField(
      "carb_share", when(energy > 0.0, carbs * 4.0 / energy).otherwise(lit(0.0))))
    // 6. Read as a number, across the join and two operators.
    val d6 = d5.withColumn("well_rated", col("grade_rank") < 0.5)

    val d7 = d6.select("barcode", "grade_label", "grade_rank", "well_rated")
    Sink.untyped(d7, Sink.scratch(spark, "Df2BadType"))

    spark.stop()
  }
}
