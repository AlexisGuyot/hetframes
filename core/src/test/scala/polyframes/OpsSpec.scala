package polyframes

import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import polyframes.ops.Ops._
import polyframes.runtime.Data
import polyframes.types.{Document, Relation}
import shapeless.labelled.FieldType
import shapeless.{::, HNil}

import scala.jdk.CollectionConverters._

/**
 * The six operators end to end, on data small enough to read by eye.
 *
 *   sbt "core/Test/runMain polyframes.OpsSpec"
 */
object OpsSpec {

  type SIdentity = FieldType["names", List[String]] :: FieldType["age", Int] :: HNil
  type SDoc      = FieldType["id", Int] :: FieldType["identity", SIdentity] :: HNil

  // Shares no attribute name with SDoc, as the join requires.
  type SCity = FieldType["person", Int] :: FieldType["city", String] :: HNil

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().master("local[*]").appName("ops").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    def fields(d: DataFrame, at: String): List[String] =
      d.schema(at).dataType.asInstanceOf[StructType].fieldNames.toList

    val identity = StructType(Seq(
      StructField("names", ArrayType(StringType)),
      StructField("age", IntegerType)
    ))
    val docs = spark.createDataFrame(
      List(
        Row(42, Row(Seq("ada", "alan"), 25)),
        Row(7, Row(Seq("maeva"), 30))
      ).asJava,
      StructType(Seq(StructField("id", IntegerType), StructField("identity", identity)))
    )
    val d0 = Data[Document, SDoc](docs)

    // 1. add, inside the nested schema (rules 38 and 37), then at the root.
    val d1 = add[Document, "identity" :: "adult" :: HNil](d0)(s => s.tail.head.tail.head >= 18)
    assert(fields(d1.df, "identity") == List("names", "age", "adult"), "nested add")
    val d2 = add[Document, "known" :: HNil](d1)(_ => true)
    assert(d2.df.schema.fieldNames.toList == List("id", "identity", "known"), "root add")
    println("1. add ok")

    // 2. update, changing the domain of a nested attribute.
    val d3 = update[Document, "identity" :: "age" :: HNil](d2)(s => s.tail.head.tail.head * 1.5)
    assert(d3.df.schema("identity").dataType.asInstanceOf[StructType]("age").dataType == DoubleType,
      "update domain")
    val ages = d3.df.select("identity.age").collect().map(_.getDouble(0)).toSet
    assert(ages == Set(37.5, 45.0), s"update values: $ages")
    println("2. update ok")

    // 3. project, announcing the relational model for atomic attributes.
    val d4 = project[Relation, ("id" :: HNil) :: ("known" :: HNil) :: HNil](d2)
    assert(d4.df.schema.fieldNames.toSet == Set("id", "known"), "project")
    println("3. project ok")

    // 4. drop and rename.
    val d5 = drop[Document, "identity" :: "names" :: HNil](d2)
    assert(!fields(d5.df, "identity").contains("names"), "drop")
    val d6 = rename[Document, "id" :: HNil, "code"](d5)
    assert(d6.df.schema.fieldNames.contains("code"), "rename")
    println("4. drop and rename ok")

    // 5. inner join with a relational table.
    val cities = spark.createDataFrame(
      List(Row(42, "Lisbon"), Row(7, "Oslo")).asJava,
      StructType(Seq(StructField("person", IntegerType), StructField("city", StringType)))
    )
    val joined = join[Document, "id" :: HNil, "person" :: HNil](d2, Data[Relation, SCity](cities))
    val out = joined.df.select("id", "city").collect()
      .map(r => (r.getInt(0), r.getString(1))).toSet
    assert(out == Set((42, "Lisbon"), (7, "Oslo")), s"join output: $out")
    println("5. join ok")

    spark.stop()
    println("OpsSpec: all checks passed")
  }
}
