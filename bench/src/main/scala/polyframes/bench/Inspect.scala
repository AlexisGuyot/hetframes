package polyframes.bench

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{col, count}
import org.apache.spark.sql.types.{ArrayType, StructType}

/**
 * A one-off survey of the extracted sample, used to choose the attributes
 * declared in Schemas.scala. Not part of the measurements.
 *
 *   sbt "bench/runMain polyframes.bench.Inspect"
 */
object Inspect {

  def main(args: Array[String]): Unit = {
    val path = args.headOption.getOrElse(Workspace.data(10000))

    val spark = SparkSession.builder().master("local[*]").appName("inspect").getOrCreate()
    spark.sparkContext.setLogLevel("ERROR")

    val df = spark.read.json(path)
    val total = df.count()
    println(s"=== $path : $total products, ${df.schema.fields.length} top-level fields ===\n")

    // Only atomic and array-of-atomic fields: the ones a schema can use.
    val usable = df.schema.fields.filter { f =>
      f.dataType match {
        case _: StructType                            => false
        case ArrayType(_: StructType, _)              => false
        case _                                        => true
      }
    }
    // One aggregation for all fields: one job per field would take minutes.
    val row = df.select(usable.map(f => count(col(s"`${f.name}`")).as(f.name)).toIndexedSeq: _*).head()
    val counts = usable.zipWithIndex.map { case (f, i) =>
      (f.name, f.dataType.simpleString, row.getLong(i))
    }.sortBy(-_._3)

    println("--- usable top-level fields, most populated first (top 60) ---")
    counts.take(60).foreach { case (n, t, c) =>
      println(f"$c%8d  ${100.0 * c / total}%5.1f%%  $t%-20s $n")
    }

    println(s"\n--- nested objects available at the top level ---")
    df.schema.fields.collect { case f if f.dataType.isInstanceOf[StructType] =>
      val st = f.dataType.asInstanceOf[StructType]
      println(f"  ${f.name}%-24s ${st.fields.length} fields")
    }

    df.schema.fields.find(_.name == "nutriments").foreach { f =>
      val st = f.dataType.asInstanceOf[StructType]
      val flat = st.fields.filter(sf => !sf.dataType.isInstanceOf[StructType])
      val subRow = df.select(
        flat.map(sf => count(col(s"`nutriments`.`${sf.name}`")).as(sf.name)).toIndexedSeq: _*
      ).head()
      val sub = flat.zipWithIndex.map { case (sf, i) =>
        (sf.name, sf.dataType.simpleString, subRow.getLong(i))
      }.sortBy(-_._3)
      println(s"\n--- nutriments, most populated first (top 40 of ${st.fields.length}) ---")
      sub.take(40).foreach { case (n, t, c) =>
        println(f"$c%8d  ${100.0 * c / total}%5.1f%%  $t%-20s $n")
      }
    }

    println("\n--- arrays of atomic values at the top level ---")
    df.schema.fields.collect {
      case f if f.dataType.isInstanceOf[ArrayType] &&
                !f.dataType.asInstanceOf[ArrayType].elementType.isInstanceOf[StructType] =>
        println(f"  ${f.name}%-32s ${f.dataType.simpleString}")
    }

    spark.stop()
  }
}
