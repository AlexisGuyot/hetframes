package polyframes.bench

import java.net.{URL, URLClassLoader}
import java.nio.file.Path

import scala.util.control.NonFatal

/** What running one program produced. */
final case class RunOutcome(
    runs: Boolean,
    millis: Long,
    peakHeapMb: Long,
    failure: String
)

/**
 * Runs a program, timing it and sampling the used heap every 50 ms. The
 * heap reading is coarse (the whole JVM, at the collector's mercy): it
 * compares pipelines run in the same conditions, nothing more.
 */
object Runner {

  /** The deepest cause: reflection wraps the real one in several layers. */
  private def describe(t: Throwable): String = {
    val root = Iterator.iterate(t)(_.getCause).takeWhile(_ != null).toList.last
    val msg = Option(root.getMessage).map(_.linesIterator.next().trim).getOrElse("")
    (root.getClass.getSimpleName + ": " + msg).take(300)
  }

  private def usedHeapMb: Long = {
    val rt = Runtime.getRuntime
    (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)
  }

  /** Runs `body`, timing it and sampling the heap every 50 ms. */
  def measure(body: () => Unit): RunOutcome = {
    System.gc()
    Thread.sleep(200L)

    @volatile var peak = usedHeapMb
    val sampler = new Thread(() => {
      try while (!Thread.currentThread().isInterrupted) {
        val now = usedHeapMb
        if (now > peak) peak = now
        Thread.sleep(50L)
      } catch { case _: InterruptedException => () }
    })
    sampler.setDaemon(true)
    sampler.start()

    val started = System.nanoTime()
    val failure =
      try { body(); "" }
      catch {
        case NonFatal(e) => describe(e)
        case e: ExceptionInInitializerError => describe(e)
      }
    val elapsed = (System.nanoTime() - started) / 1000000L

    sampler.interrupt()
    RunOutcome(failure.isEmpty, elapsed, peak, failure)
  }

  /** Compiles a variant into a directory of its own, loads it, runs its `main`. */
  def compileAndRun(source: Path, objectName: String, args: Array[String]): RunOutcome = {
    Compiler.withTempDir("polyframes-run") { out =>
      val compiled = Compiler.compileTo(source, out)
      if (!compiled.compiles)
        RunOutcome(runs = false, 0L, 0L, "did not compile: " + compiled.firstError)
      else {
        // The child loader holds only the compiled directory and delegates
        // to the harness's loader: reloading Spark in isolation breaks its
        // static initialisers. The context loader is set for the parts of
        // Spark that read it (not encoder derivation: see VariantClasses).
        val loader = new URLClassLoader(Array[URL](out.toUri.toURL), getClass.getClassLoader)
        val previous = Thread.currentThread().getContextClassLoader
        Thread.currentThread().setContextClassLoader(loader)
        try measure { () =>
          // The class X (not X$) carries the static forwarder to main.
          loader.loadClass(objectName).getMethod("main", classOf[Array[String]]).invoke(null, args)
          ()
        }
        finally Thread.currentThread().setContextClassLoader(previous)
      }
    }
  }
}
