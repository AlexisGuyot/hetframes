package polyframes.bench

import java.nio.file.{Files, Path}

import scala.reflect.io.{Directory, PlainDirectory}
import scala.tools.nsc.reporters.{Reporter, StoreReporter}
import scala.tools.nsc.{Global, Settings}

/** What compiling one file produced. */
final case class CompileOutcome(
    compiles: Boolean,
    millis: Long,
    firstError: String
)

/**
 * Compiles one file at a time, in process, against the classpath of the
 * harness. In process rather than through sbt, so that only compilation is
 * timed, and so that a file that must fail does not stop the others. The
 * JVM is therefore warm: the figures compare files, they are not absolute.
 */
object Compiler {

  /** Already holds Spark, Shapeless and the library (set up by sbt). */
  private val classpath: String = sys.props("java.class.path")

  /** A fresh compiler writing into `out`. */
  def newGlobal(out: Path, reporter: Settings => Reporter): Global = {
    val settings = new Settings()
    settings.classpath.value = classpath
    settings.usejavacp.value = false
    settings.outputDirs.setSingleOutput(new PlainDirectory(new Directory(out.toFile)))
    new Global(settings, reporter(settings))
  }

  /** Runs `body` with a temporary directory, deleted afterwards. */
  def withTempDir[A](prefix: String)(body: Path => A): A = {
    val dir = Files.createTempDirectory(prefix)
    try body(dir)
    finally { Directory(dir.toFile).deleteRecursively(); () }
  }

  /** Prints a line every 30 s, so that a stuck compilation shows. */
  private def beat(name: String, startedNanos: Long): Thread = {
    val t = new Thread(() => {
      try while (!Thread.currentThread().isInterrupted) {
        Thread.sleep(30000L)
        val s = (System.nanoTime() - startedNanos) / 1000000000L
        println(s"  ... still compiling $name after $s s")
      } catch { case _: InterruptedException => () }
    })
    t.setDaemon(true)
    t.start()
    t
  }

  def compile(source: Path): CompileOutcome =
    withTempDir("polyframes-compile")(out => compileTo(source, out))

  /** Compiles into a directory the caller owns, to load and run it after. */
  def compileTo(source: Path, out: Path): CompileOutcome = {
    var store: StoreReporter = null
    val global = newGlobal(out, s => { store = new StoreReporter(s); store })

    val started = System.nanoTime()
    val heartbeat = beat(source.getFileName.toString, started)
    try new global.Run().compile(List(source.toAbsolutePath.toString))
    finally heartbeat.interrupt()
    val elapsed = (System.nanoTime() - started) / 1000000L

    val error = store.infos
      .find(_.severity == store.ERROR)
      .map(i => i.msg.linesIterator.next().trim)
      .getOrElse("")

    // A Global indexes the whole classpath, Spark included; left to pile
    // up, a few dozen of them fill the heap and the run stalls in GC.
    global.close()
    System.gc()

    CompileOutcome(!store.hasErrors, elapsed, error)
  }

  /** Every file compiled per `Protocol.interleaved`; the median time is kept. */
  def timedInterleaved(sources: Seq[Path], rounds: Int): Map[Path, CompileOutcome] =
    Protocol.interleaved(sources, rounds)(s => { compile(s); () })(compile)
      .map { case (s, outcomes) =>
        s -> outcomes.head.copy(millis = Protocol.median(outcomes.map(_.millis)))
      }.toMap
}
