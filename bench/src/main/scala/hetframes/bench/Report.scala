package hetframes.bench

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import scala.io.Source
import scala.util.{Try, Using}

/**
 * Writes RESULTS.md from results.csv, and from nothing else, so that the
 * report can be rebuilt without measuring again.
 */
object Report {

  final case class Row(experiment: String, subject: String, baseline: String,
                       family: String, volume: String, width: String,
                       metric: String, value: String, note: String)

  /** One CSV line, with quoted cells and doubled quotes. */
  private def parse(line: String): List[String] = {
    val out = List.newBuilder[String]
    val cell = new StringBuilder
    var quoted = false
    var i = 0
    while (i < line.length) {
      val c = line.charAt(i)
      if (quoted) {
        if (c == '"') {
          if (i + 1 < line.length && line.charAt(i + 1) == '"') { cell += '"'; i += 1 }
          else quoted = false
        } else cell += c
      } else if (c == '"') quoted = true
      else if (c == ',') { out += cell.toString; cell.clear() }
      else cell += c
      i += 1
    }
    out += cell.toString
    out.result()
  }

  /** A markdown table, in the layout RESULTS.md has always had. */
  private def table(header: Seq[String], rows: Seq[Seq[String]]): String = {
    def line(cells: Seq[String]) = cells.mkString("| ", " | ", " |\n")
    line(header) + header.map(_ => "---").mkString("|", "|", "|\n") + rows.map(line).mkString
  }

  private val Families = List(
    "noattr" -> "an attribute absent from the schema",
    "badtype" -> "an attribute of an unexpected domain",
    "badmodel" -> "data in an unexpected model",
    "badtransfo" -> "a transformation breaking the announced model")
  private val Baselines = List("hetframes" -> "hf", "dataframe" -> "df", "dataset" -> "ds")
  private val Correct = List("p1", "p1-dataframe", "p1-dataset", "p2", "p2-dataframe", "p2-dataset")

  def main(args: Array[String]): Unit = {
    if (!Files.exists(Results.path)) {
      System.err.println(s"${Results.path} not found. Run the experiments first.")
      sys.exit(1)
    }

    val rows = Using.resource(Source.fromFile(Results.path.toFile, "UTF-8")) { src =>
      src.getLines().drop(1).filter(_.nonEmpty).map(parse).collect {
        case List(e, s, b, f, v, w, m, value, n) => Row(e, s, b, f, v, w, m, value, n)
      }.toList
    }

    /** The first value recorded for a subject and a metric (and a volume). */
    def value(subject: String, metric: String, volume: String = null): Option[String] =
      rows.find(r => r.subject == subject && r.metric == metric &&
        (volume == null || (r.experiment == "volume" && r.volume == volume))).map(_.value)

    val sb = new StringBuilder
    sb ++= "# Results\n\n"
    sb ++= "Produced by `sbt experiments`, then `sbt \"bench/runMain hetframes.bench.Report\"`.\n"
    sb ++= "Every figure below is a row of `results.csv` and nothing else.\n"
    sb ++= "The machine, the pinned versions and the extracted data are described in\n"
    sb ++= "`README.md` and `data/PROVENANCE.txt`.\n\n"

    // Where each error is caught.
    List(("P1", ""), ("P2", "2")).foreach { case (pipeline, suffix) =>
      sb ++= s"## Where each error is caught, $pipeline\n\n"
      sb ++= table("error" +: Baselines.map(_._1), Families.map { case (family, label) =>
        label +: Baselines.map { case (_, prefix) =>
          val name = s"$prefix$suffix-$family"
          (value(name, "compiles"), value(name, "runs")) match {
            case (Some("false"), _)          => "compile time"
            case (Some("true"), Some("false")) => "run time"
            case (Some("true"), Some("true"))  => "**not detected**"
            case (Some("true"), _)           => "not run"
            case _                           => "-"
          }
        }
      })
      sb ++= "\n"
    }

    sb ++= "## Compiling the correct pipelines\n\n"
    sb ++= table(Seq("pipeline", "compilation (ms)"),
      Correct.map(s => Seq(s, value(s, "compile-ms").getOrElse("-"))))

    val volumes = rows.filter(_.experiment == "volume").map(_.volume).distinct
      .flatMap(v => Try(v.toInt).toOption).sorted
    if (volumes.nonEmpty) {
      sb ++= "\n## Running the correct pipelines\n\n"
      sb ++= table("products" +: Correct.flatMap(s => List(s"$s ms", s"$s MB")),
        volumes.map { n =>
          n.toString +: Correct.flatMap(s => List("run-ms", "heap-mb")
            .map(m => value(s, m, n.toString).getOrElse("-")))
        })
    }

    val widths = rows.filter(_.experiment == "width")
      .flatMap(r => Try(r.width.toInt).toOption.map(_ -> r.value)).sortBy(_._1)
    if (widths.nonEmpty) {
      sb ++= "\n## Compiling a declaration of growing width\n\n"
      sb ++= table(Seq("declared attributes", "compilation (ms)"),
        widths.map { case (w, v) => Seq(w.toString, v) })
    }

    // Guard: variants of one family may share a failure message, but one
    // message shared across families means the harness, not the injected
    // error, made them fail.
    val failures = rows.filter(r => r.metric == "runs" && r.value == "false" && r.note.nonEmpty)
    val acrossFamilies = failures.groupBy(_.note)
      .filter { case (_, all) => all.map(_.family).distinct.size > 1 }
    if (acrossFamilies.nonEmpty) {
      sb ++= "\n## Warning\n\n"
      sb ++= "One failure is shared by variants of different families, which means\n"
      sb ++= "they failed for a reason of their own rather than for the error each\n"
      sb ++= "was written to exhibit:\n\n"
      acrossFamilies.foreach { case (msg, all) =>
        sb ++= s"- ${all.map(_.subject).mkString(", ")}: `$msg`\n"
      }
    }

    sb ++= "\n## What each baseline reports, and when\n\n"
    sb ++= table(Seq("variant", "failure"),
      failures.map(r => Seq(r.subject, r.note.take(120).replace("|", "\\|"))))

    sb ++= "\n## Reading these figures\n\n"
    sb ++= "Compilation is timed in process, on a warm JVM: every file is compiled\n"
    sb ++= "once unmeasured, then in interleaved rounds, and the median of the\n"
    sb ++= "rounds is kept. The figures are therefore lower than what a user\n"
    sb ++= "waiting on a cold build would see, and are meant for comparing the\n"
    sb ++= "variants with one another. Execution follows the same protocol.\n\n"
    sb ++= "Memory is sampled, not computed: a thread reads the used heap every\n"
    sb ++= "50 ms and the highest reading is kept. It sees the whole virtual\n"
    sb ++= "machine, not the pipeline alone.\n"

    Files.write(Workspace.resolve("RESULTS.md"), sb.toString.getBytes(StandardCharsets.UTF_8))
    println(sb.toString)
    println(s"Wrote ${Workspace.resolve("RESULTS.md")}")
  }
}
