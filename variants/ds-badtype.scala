package hetframes.variants

import org.apache.spark.sql.{Encoders, SparkSession}
import hetframes.bench.P1Dataset._
import hetframes.bench.Sink

/**
 * Datasets, an attribute of an unexpected domain.
 *
 * Same injection as hf-badtype and df-badtype: carb_share is created as a
 * string at step 1 and read as a number at step 4. With Datasets, the
 * classes describing the intermediate schemas have to be rewritten to
 * carry the mistaken domain, which the compiler then checks.
 *
 * Expected: rejected at compile time, at step 4.
 */
object DsBadType {

  case class NutrimentsLabel(
      `energy-kcal_100g`: Option[Double], fat_100g: Option[Double],
      carbohydrates_100g: Option[Double], proteins: Option[Double],
      salt: Option[Double], carb_share: String)
  case class ProductLabel(
      code: String, product_type: String, nutriscore_grade: String,
      pnns_groups_1: String, completeness: Option[Double],
      countries_tags: Seq[String], nutriments: NutrimentsLabel)
  case class ProductBeverageLabel(
      code: String, product_type: String, nutriscore_grade: String,
      pnns_groups_1: String, completeness: Option[Double],
      countries_tags: Seq[String], nutriments: NutrimentsLabel,
      is_beverage: Boolean)
  case class CarbShareLabel(carb_share: String)
  case class KeptLabel(code: String, nutriscore_grade: String,
                       is_beverage: Boolean, nutriments: CarbShareLabel)
  case class KeptDenseLabel(code: String, nutriscore_grade: String, is_beverage: Boolean,
                            nutriments: CarbShareLabel, dense: Boolean)

  def main(args: Array[String]): Unit = {
    val path = args.headOption.getOrElse("data/products-10000.jsonl")
    val spark = SparkSession.builder().master("local[*]").appName("DsBadType").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
    import spark.implicits._

    val d0 = spark.read.schema(Encoders.product[Product].schema).json(path).as[Product]

    // 1. The mistake: the share is created as a label, not as a fraction.
    val d1 = d0.map { p =>
      val n = p.nutriments
      ProductLabel(p.code, p.product_type, p.nutriscore_grade, p.pnns_groups_1,
        p.completeness, p.countries_tags,
        NutrimentsLabel(n.flatMap(_.`energy-kcal_100g`), n.flatMap(_.fat_100g),
          n.flatMap(_.carbohydrates_100g), n.flatMap(_.proteins),
          n.flatMap(_.salt), "high"))
    }
    val d2 = d1.map(p => ProductBeverageLabel(p.code, p.product_type, p.nutriscore_grade,
      p.pnns_groups_1, p.completeness, p.countries_tags, p.nutriments,
      p.pnns_groups_1 == "Beverages"))
    val d3 = d2.map(p => KeptLabel(p.code, p.nutriscore_grade, p.is_beverage,
      CarbShareLabel(p.nutriments.carb_share)))

    // 4. Read as a number, two operators after it was created.
    val d4 = d3.map(p => KeptDenseLabel(p.code, p.nutriscore_grade, p.is_beverage,
      p.nutriments, p.nutriments.carb_share > 0.5))
    val d5 = d4.map(p => Flat(p.code, p.nutriscore_grade, p.is_beverage, p.dense))
    val d6 = d5.map(p => Flagged(p.code, p.nutriscore_grade, p.is_beverage, p.dense,
      p.dense && !p.is_beverage))
    Sink.ofDataset(d6, Sink.scratch(spark, "DsBadType"))

    spark.stop()
  }
}
