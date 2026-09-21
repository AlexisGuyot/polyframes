package polyframes.bench

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

/**
 * One measurement, in long form.
 *
 * Every figure section 5 reports is a row of this shape, so that adding an
 * axis never means changing the file format. Fields that do not apply to a
 * given measurement are left empty rather than filled with a placeholder.
 */
final case class Measurement(
    experiment: String,   // error-prevention, overhead, volume, width
    subject: String,      // the variant or pipeline measured
    baseline: String,     // polyframes, dataframe, dataset
    family: String,       // none, noattr, badtype, badmodel, badtransfo
    volume: String,       // number of products, when it varies
    width: String,        // number of declared attributes, when it varies
    metric: String,       // compile-ms, run-ms, heap-mb, compiles, runs
    value: String,
    note: String
)

object Results {

  private val Header =
    "experiment,subject,baseline,family,volume,width,metric,value,note"

  private def escape(s: String): String =
    if (s.contains(",") || s.contains("\"")) "\"" + s.replace("\"", "\"\"") + "\""
    else s

  def path: Path = Workspace.resolve("results.csv")

  def start(): Unit = {
    Files.write(path, (Header + "\n").getBytes(StandardCharsets.UTF_8))
    ()
  }

  def append(m: Measurement): Unit = {
    val row = List(m.experiment, m.subject, m.baseline, m.family, m.volume,
      m.width, m.metric, m.value, m.note).map(escape).mkString(",")
    Files.write(path, (row + "\n").getBytes(StandardCharsets.UTF_8),
      StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    ()
  }

  def appendAll(ms: Seq[Measurement]): Unit = ms.foreach(append)
}
