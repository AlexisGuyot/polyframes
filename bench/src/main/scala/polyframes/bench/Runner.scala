package polyframes.bench

import java.net.{URL, URLClassLoader}
import java.nio.file.{Files, Path}

import scala.reflect.io.Directory
import scala.util.control.NonFatal

/** What running one program produced. */
final case class RunOutcome(
    runs: Boolean,
    millis: Long,
    peakHeapMb: Long,
    failure: String
)

/**
 * Runs a program and watches what it costs.
 *
 * Memory is sampled rather than computed: a thread polls the used heap
 * while the program runs and keeps the highest reading. That is a coarse
 * instrument. It sees the whole JVM, not the pipeline alone, and what it
 * reports depends on when the collector happens to run. It is used here to
 * compare three pipelines doing the same work in the same conditions, not
 * to state what any of them requires.
 */
object Runner {

  /** The deepest cause, named and quoted. Reflection wraps what a program
    * throws in two or three layers, and the outermost of them says nothing
    * about why the program failed. */
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

  /**
   * Compiles a variant to a directory of its own, loads it there, and runs
   * its main method. The variants that compile have to be run this way:
   * they are not part of any source set, so nothing else puts them on a
   * classpath.
   */
  def compileAndRun(source: Path, objectName: String, args: Array[String]): RunOutcome = {
    val out = Files.createTempDirectory("polyframes-run")
    try {
      val compiled = Compiler.compileTo(source, out)
      if (!compiled.compiles)
        RunOutcome(runs = false, 0L, 0L, "did not compile: " + compiled.firstError)
      else {
        // Only the freshly compiled directory goes in the child loader.
        // Giving it no parent would reload Spark in isolation, and Spark
        // does not survive that: its static initialisers fail, and every
        // variant then dies of the same unrelated cause before reaching
        // the error it was written to exhibit.
        val loader = new URLClassLoader(Array[URL](out.toUri.toURL), getClass.getClassLoader)
        // Set for the parts of Spark that read it. Note that it is not
        // enough for encoder derivation, which goes through the mirror
        // carried by a type tag captured at compile time rather than
        // through the thread's loader: a case class a variant declares
        // itself stays invisible to it. See VariantClasses.
        val previous = Thread.currentThread().getContextClassLoader
        Thread.currentThread().setContextClassLoader(loader)
        try measure { () =>
          // A top-level Scala object compiles to a class named X$ whose
          // main is an instance method, and to a class named X carrying a
          // static forwarder to it. It is the forwarder that can be called
          // with a null receiver; the module class cannot.
          val cls = loader.loadClass(objectName)
          val main = cls.getMethod("main", classOf[Array[String]])
          main.invoke(null, args)
          ()
        }
        finally Thread.currentThread().setContextClassLoader(previous)
      }
    } finally {
      Directory(out.toFile).deleteRecursively()
      ()
    }
  }
}
