package polyframes.bench

import java.nio.file.Path

/**
 * The fifteen programs section 5 compiles: the reference pipeline written
 * three ways, and the four families of engineering errors injected into
 * each of the three.
 *
 * The three correct pipelines are read from where they live, in the source
 * tree of the harness, rather than copied into `variants/`. Only the
 * erroneous ones need to sit outside a source directory, and only because
 * four of them must not compile.
 */
object Variants {

  final case class Variant(
      name: String,
      baseline: String,
      family: String,
      source: Path,
      expectedToCompile: Boolean,
      objectName: String = ""
  )

  private def bench(file: String): Path =
    Workspace.resolve("bench", "src", "main", "scala", "polyframes", "bench", file)

  private def variant(file: String): Path = Workspace.resolve("variants", file)

  val All: List[Variant] = List(
    Variant("p1",            "polyframes", "none",       bench("P1.scala"),          true,  "polyframes.bench.P1"),
    Variant("p1-dataframe",  "dataframe",  "none",       bench("P1DataFrame.scala"), true,  "polyframes.bench.P1DataFrame"),
    Variant("p1-dataset",    "dataset",    "none",       bench("P1Dataset.scala"),   true,  "polyframes.bench.P1Dataset"),

    Variant("pf-noattr",     "polyframes", "noattr",     variant("pf-noattr.scala"),     false, "polyframes.variants.PfNoAttr"),
    Variant("pf-badtype",    "polyframes", "badtype",    variant("pf-badtype.scala"),    false, "polyframes.variants.PfBadType"),
    Variant("pf-badmodel",   "polyframes", "badmodel",   variant("pf-badmodel.scala"),   false, "polyframes.variants.PfBadModel"),
    Variant("pf-badtransfo", "polyframes", "badtransfo", variant("pf-badtransfo.scala"), false, "polyframes.variants.PfBadTransfo"),

    Variant("df-noattr",     "dataframe",  "noattr",     variant("df-noattr.scala"),     true,  "polyframes.variants.DfNoAttr"),
    Variant("df-badtype",    "dataframe",  "badtype",    variant("df-badtype.scala"),    true,  "polyframes.variants.DfBadType"),
    Variant("df-badmodel",   "dataframe",  "badmodel",   variant("df-badmodel.scala"),   true,  "polyframes.variants.DfBadModel"),
    Variant("df-badtransfo", "dataframe",  "badtransfo", variant("df-badtransfo.scala"), true,  "polyframes.variants.DfBadTransfo"),

    Variant("ds-noattr",     "dataset",    "noattr",     variant("ds-noattr.scala"),     false, "polyframes.variants.DsNoAttr"),
    Variant("ds-badtype",    "dataset",    "badtype",    variant("ds-badtype.scala"),    false, "polyframes.variants.DsBadType"),
    Variant("ds-badmodel",   "dataset",    "badmodel",   variant("ds-badmodel.scala"),   true,  "polyframes.variants.DsBadModel"),
    Variant("ds-badtransfo", "dataset",    "badtransfo", variant("ds-badtransfo.scala"), true,  "polyframes.variants.DsBadTransfo"),

    // P2: the same grid, on the pipeline that integrates two sources with
    // the whole palette of operators.
    Variant("p2",            "polyframes", "none",       bench("P2.scala"),          true,  "polyframes.bench.P2"),
    Variant("p2-dataframe",  "dataframe",  "none",       bench("P2DataFrame.scala"), true,  "polyframes.bench.P2DataFrame"),
    Variant("p2-dataset",    "dataset",    "none",       bench("P2Dataset.scala"),   true,  "polyframes.bench.P2Dataset"),

    Variant("pf2-noattr",     "polyframes", "noattr",     variant("pf2-noattr.scala"),     false, "polyframes.variants.Pf2NoAttr"),
    Variant("pf2-badtype",    "polyframes", "badtype",    variant("pf2-badtype.scala"),    false, "polyframes.variants.Pf2BadType"),
    Variant("pf2-badmodel",   "polyframes", "badmodel",   variant("pf2-badmodel.scala"),   false, "polyframes.variants.Pf2BadModel"),
    Variant("pf2-badtransfo", "polyframes", "badtransfo", variant("pf2-badtransfo.scala"), false, "polyframes.variants.Pf2BadTransfo"),

    Variant("df2-noattr",     "dataframe",  "noattr",     variant("df2-noattr.scala"),     true,  "polyframes.variants.Df2NoAttr"),
    Variant("df2-badtype",    "dataframe",  "badtype",    variant("df2-badtype.scala"),    true,  "polyframes.variants.Df2BadType"),
    Variant("df2-badmodel",   "dataframe",  "badmodel",   variant("df2-badmodel.scala"),   true,  "polyframes.variants.Df2BadModel"),
    Variant("df2-badtransfo", "dataframe",  "badtransfo", variant("df2-badtransfo.scala"), true,  "polyframes.variants.Df2BadTransfo"),

    Variant("ds2-noattr",     "dataset",    "noattr",     variant("ds2-noattr.scala"),     false, "polyframes.variants.Ds2NoAttr"),
    Variant("ds2-badtype",    "dataset",    "badtype",    variant("ds2-badtype.scala"),    false, "polyframes.variants.Ds2BadType"),
    Variant("ds2-badmodel",   "dataset",    "badmodel",   variant("ds2-badmodel.scala"),   true,  "polyframes.variants.Ds2BadModel"),
    Variant("ds2-badtransfo", "dataset",    "badtransfo", variant("ds2-badtransfo.scala"), true,  "polyframes.variants.Ds2BadTransfo")
  )

  /** Which pipeline a variant belongs to, read off its name. */
  def pipelineOf(v: Variant): String = if (v.name.contains("2")) "P2" else "P1"

  /** The width axis: the same three steps over declarations of growing
    * width. Only their compilation is measured. */
  val Widths: List[(Int, Variant)] = List(5, 10, 20, 40, 80).map { n =>
    n -> Variant(s"width-$n", "polyframes", "none",
      variant(s"width-$n.scala"), true, s"polyframes.variants.Width$n")
  }
}
