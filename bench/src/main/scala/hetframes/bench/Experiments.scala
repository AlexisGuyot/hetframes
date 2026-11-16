package hetframes.bench

import java.nio.file.Files

/**
 * The protocol of section 5, in four programs that `sbt experiments` runs
 * in turn, each in a JVM of its own: CompileWidth, CompileVariants,
 * RunExperiments, then Report. Compiling and running are kept apart so
 * that the compiler's heap is not counted as a pipeline's, and the width
 * axis is measured first and alone because it is the most sensitive to
 * the state of the heap after many compilations.
 */
object Protocol {

  /** Measured rounds when no argument is given. Odd, so that the median is
    * one of the measurements. */
  val Rounds: Int = 5

  def median(xs: Seq[Long]): Long = { val s = xs.sorted; s(s.length / 2) }

  /**
   * One unmeasured pass over `keys` (`warm`), then `rounds` passes measuring
   * every key once, in the same order, so that no key is measured in a JVM
   * warmer or colder than the others. Returns every measurement of each key.
   */
  def interleaved[K, O](keys: Seq[K], rounds: Int)(warm: K => Unit)(measure: K => O): Seq[(K, Seq[O])] = {
    keys.foreach(warm)
    val measured = (1 to rounds).map(_ => keys.map(measure))
    keys.indices.map(i => keys(i) -> measured.map(_(i)))
  }

  def roundsFrom(args: Array[String]): Int = args.headOption.map(_.toInt).getOrElse(Rounds)

  def interleavedNote(rounds: Int): String = s"median of $rounds interleaved rounds"
}

object CompileWidth {

  def main(args: Array[String]): Unit = {
    val runs = Protocol.roundsFrom(args)
    println(s"project root: ${Workspace.root}")

    // Preflight, once for the four programs: if the library is not visible
    // to the compiler, every file would fail for that reason alone.
    val probe = Files.createTempFile("hetframes-probe", ".scala")
    Files.write(probe, ("object HetFramesProbe { " +
      "val m: hetframes.types.Model = null; " +
      "val r = hetframes.bench.Workspace.root }").getBytes("UTF-8"))
    val preflight = Compiler.compile(probe)
    Files.deleteIfExists(probe)
    if (!preflight.compiles) {
      System.err.println("The library is not visible to the compiler: " + preflight.firstError)
      System.err.println("Run `sbt bench/compile` first.")
      sys.exit(1)
    }
    println("preflight: the library is on the compiler's classpath")

    val missing = (Variants.All ++ Variants.Widths.map(_._2))
      .filterNot(v => Files.exists(v.source))
    if (missing.nonEmpty) {
      System.err.println("Missing source files:")
      missing.foreach(v => System.err.println("  " + v.source))
      sys.exit(1)
    }

    Results.start()

    println("\n=== compiling declarations of growing width ===")
    val timed = Compiler.timedInterleaved(Variants.Widths.map(_._2.source), runs)
    Variants.Widths.foreach { case (width, v) =>
      val o = timed(v.source)
      println(f"$width%4d attributes  ${o.millis}%7d ms  compiles=${o.compiles}  " +
        o.firstError.take(60))
      Results.append(Measurement("width", v.name, "hetframes", "none", "",
        width.toString, "compile-ms", o.millis.toString,
        if (o.compiles) Protocol.interleavedNote(runs) else o.firstError))
    }

    println(s"\nAppended to ${Results.path}")
  }
}

object CompileVariants {

  def main(args: Array[String]): Unit = {
    val runs = Protocol.roundsFrom(args)
    println(s"project root: ${Workspace.root}")

    println("\n=== compiling every variant ===")
    val timed = Compiler.timedInterleaved(Variants.All.map(_.source), runs)
    Variants.All.foreach { v =>
      val o = timed(v.source)
      val agrees = if (o.compiles == v.expectedToCompile) "" else "   <-- UNEXPECTED"
      println(f"${v.name}%-15s compiles=${o.compiles}%-6s ${o.millis}%6d ms  " +
        o.firstError.take(70) + agrees)
      Results.appendAll(Seq(
        Measurement("error-prevention", v.name, v.baseline, v.family, "", "",
          "compiles", o.compiles.toString, o.firstError),
        Measurement("error-prevention", v.name, v.baseline, v.family, "", "",
          "compile-ms", o.millis.toString, Protocol.interleavedNote(runs))
      ))
    }

    println(s"\nAppended to ${Results.path}")
  }
}

/** Runs the variants that compile, then times the correct pipelines at each volume. */
object RunExperiments {

  val reference: Int = 10000

  def main(args: Array[String]): Unit = {
    val runs = Protocol.roundsFrom(args)
    println(s"project root: ${Workspace.root}")
    Workspace.clearOut()
    val inputs = Array(Workspace.data(reference), Workspace.grades)

    // Preflight: a pipeline known to work goes through the same loading
    // machinery first, so that a broken harness cannot pass for variants
    // failing as expected.
    val check = Runner.compileAndRun(Variants.All.head.source, Variants.All.head.objectName, inputs)
    if (!check.runs) {
      System.err.println("The reference pipeline does not run through the harness: " + check.failure)
      System.err.println("Nothing can be concluded from the variants until this passes.")
      sys.exit(1)
    }
    println(f"preflight: the reference pipeline runs (${check.millis}%d ms)")

    println("\n=== running the variants that compile ===")
    Variants.All.filter(_.family != "none").foreach { v =>
      val o = Runner.compileAndRun(v.source, v.objectName, inputs)
      if (o.failure.startsWith("did not compile"))
        println(f"${v.name}%-15s (rejected at compile time, nothing to run)")
      else {
        println(f"${v.name}%-15s runs=${o.runs}%-6s  ${o.failure.take(80)}")
        Results.append(Measurement("error-prevention", v.name, v.baseline, v.family,
          "", "", "runs", o.runs.toString, o.failure))
      }
    }

    println("\n=== the correct pipelines, at each volume ===")
    val points = FetchData.VolumePoints
      .filter(n => Files.exists(Workspace.resolve("data", s"products-$n.jsonl")))
    if (points.isEmpty) println("no extracted data found; run FetchData first")

    points.foreach { n =>
      val path = Workspace.data(n)
      def runOnce(suffix: String)(p: Pipelines.Pipeline): RunOutcome =
        Pipelines.withSpark(s"${p.name}-$n$suffix")(s => Runner.measure(() => p.run(s, path)))

      val measured = Protocol.interleaved(Pipelines.Correct, runs)(
        p => { runOnce("-warmup")(p); () })(runOnce(""))

      measured.foreach { case (p, outcomes) =>
        val time = Protocol.median(outcomes.map(_.millis))
        val heap = Protocol.median(outcomes.map(_.peakHeapMb))
        val failed = outcomes.filterNot(_.runs).map(_.failure).headOption.getOrElse("")
        println(f"$n%8d  ${p.name}%-15s $time%6d ms  $heap%5d MB  $failed")
        Results.appendAll(Seq(
          Measurement("volume", p.name, p.baseline, "none", n.toString, "", "run-ms",
            time.toString, Protocol.interleavedNote(runs)),
          Measurement("volume", p.name, p.baseline, "none", n.toString, "", "heap-mb",
            heap.toString, "peak sampled every 50 ms")
        ))
      }
    }
    println(s"\nAppended to ${Results.path}")
  }
}

/** The six correct pipelines, and how to run one. */
object Pipelines {

  final case class Pipeline(
      name: String,
      baseline: String,
      run: (org.apache.spark.sql.SparkSession, String) => Unit
  )

  val Correct: List[Pipeline] = List(
    Pipeline("p1", "hetframes",
      (s, p) => Sink.typed(P1(s, p), Workspace.out("p1"))),
    Pipeline("p1-dataframe", "dataframe",
      (s, p) => Sink.untyped(P1DataFrame(s, p), Workspace.out("p1-dataframe"))),
    Pipeline("p1-dataset", "dataset",
      (s, p) => Sink.ofDataset(P1Dataset(s, p), Workspace.out("p1-dataset"))),
    Pipeline("p2", "hetframes",
      (s, p) => Sink.typed(P2(s, p, Workspace.grades), Workspace.out("p2"))),
    Pipeline("p2-dataframe", "dataframe",
      (s, p) => Sink.untyped(P2DataFrame(s, p, Workspace.grades), Workspace.out("p2-dataframe"))),
    Pipeline("p2-dataset", "dataset",
      (s, p) => Sink.ofDataset(P2Dataset(s, p, Workspace.grades), Workspace.out("p2-dataset")))
  )

  def withSpark[A](name: String)(body: org.apache.spark.sql.SparkSession => A): A = {
    val spark = org.apache.spark.sql.SparkSession.builder()
      .master("local[*]").appName(name).getOrCreate()
    spark.sparkContext.setLogLevel("ERROR")
    try body(spark) finally spark.stop()
  }
}
