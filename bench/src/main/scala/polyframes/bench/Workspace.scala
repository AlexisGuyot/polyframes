package polyframes.bench

import java.nio.file.{Files, Path, Paths}

/**
 * Where the project is on disk.
 *
 * Nothing here may depend on the directory a program happens to be
 * launched from. sbt runs a forked process from the base directory of the
 * subproject, an IDE from wherever it likes, and a reviewer following the
 * README from the root of the repository. The root is therefore found by
 * walking up from the current directory until a build.sbt appears, and
 * every path the harness uses is resolved against it.
 */
object Workspace {

  lazy val root: Path = {
    val start = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    Iterator.iterate(start)(_.getParent)
      .takeWhile(_ != null)
      .find(d => Files.exists(d.resolve("build.sbt")))
      .getOrElse(start)
  }

  def resolve(segments: String*): Path =
    segments.foldLeft(root)((p, s) => p.resolve(s))

  /** The extracted subset holding `products` products. */
  def data(products: Int): String =
    resolve("data", s"products-$products.jsonl").toString

  /**
   * Where a pipeline writes its result.
   *
   * A fresh directory every time, outside the repository. Spark clears an
   * output directory before writing to it, and on Windows that clearing
   * fails on files a previous run left behind, so a stable path turns the
   * second measurement of a pipeline into an error. Nothing here needs the
   * output to be kept: writing it is how a pipeline is made to run, and
   * how a sink that only accepts relational data is made to complain.
   */
  def out(name: String): String =
    Paths.get(sys.props("java.io.tmpdir"), "polyframes-out",
      s"$name-${System.nanoTime()}").toString

  /** Best-effort removal of everything the runs above wrote. */
  def clearOut(): Unit = {
    val base = Paths.get(sys.props("java.io.tmpdir"), "polyframes-out")
    if (Files.exists(base)) {
      try {
        Files.walk(base).sorted(java.util.Comparator.reverseOrder[Path]())
          .forEach(p => { Files.deleteIfExists(p); () })
      } catch { case _: Exception => () }
    }
  }

  /** The reference table P2 integrates with. Versioned with the code, not
    * extracted from the dump, and therefore outside data/. */
  def grades: String = resolve("reference", "nutriscore-grades.csv").toString
}
