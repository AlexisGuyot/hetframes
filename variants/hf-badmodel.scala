package hetframes.variants

import org.apache.spark.sql.SparkSession
import hetframes.bench.Record._
import hetframes.bench.Schemas._
import hetframes.bench.{Loader, Sink}
import hetframes.ops.Ops._
import hetframes.types.{Document, Relation}
import shapeless.{::, HNil}

/**
 * HetFrames, data in a model the operator does not accept.
 *
 * Expected: rejected at compile time.
 */
object HfBadModel {

  type KeepNested =
    ("code" :: HNil) :: ("nutriscore_grade" :: HNil) ::
    ("is_beverage" :: HNil) :: ("nutriments" :: "carb_share" :: HNil) :: HNil

  type KeepFlat =
    ("code" :: HNil) :: ("nutriscore_grade" :: HNil) ::
    ("is_beverage" :: HNil) :: ("dense" :: HNil) :: HNil

  def main(args: Array[String]): Unit = {
    val path = args.headOption.getOrElse("data/products-10000.jsonl")
    val spark = SparkSession.builder().master("local[*]").appName("HfBadModel").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val d0 = Loader.json[Document, SProduct](spark, path)
    val d1 = add[Document, "nutriments" :: "carb_share" :: HNil](d0) { s =>
      val n = s.at["nutriments"]
      val e = n.at["energy-kcal_100g"]
      if (e > 0.0) n.at["carbohydrates_100g"] * 4.0 / e else 0.0
    }
    val d2 = add[Document, "is_beverage" :: HNil](d1)(s => s.at["pnns_groups_1"] == "Beverages")
    // The sink requires relational data; the document data of step 2 is
    // handed to it as is.
    Sink.typed(d2, Sink.scratch(spark, "HfBadModel"))

    spark.stop()
  }
}
