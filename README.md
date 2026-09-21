# PolyFrames

Companion proof of concept for the article *Leveraging Types to Improve the Robustness of Analytical Pipelines in Schema-on-Read Systems*.

PolyFrames is a typed layer over SparkSQL `DataFrame`s. It encodes the schema and the logical model of data in types, and infers how both evolve along a pipeline, so that a compiler rejects transformations and compositions that would otherwise fail at run time.

It is a proof of concept, not a general-purpose library (yet). Its kernel implements the rules of the article and nothing beyond them.

**Declaration of generative AI and AI-assisted technologies :** The Opus 4.8 model of the generative AI Claude was used to accelerate, standardise and document the development of the Proof of Concept, as well as to generate several tests, based on the article and a preliminary version of the project (see https://github.com/AlexisGuyot/Pridwen/tree/master/src/main/scala). The generated code was meticulously reviewed and verified at every stage of the process.

## Reproducing the experiments

PolyFrames runs on Windows, Linux and macOS. The measurement itself is a single command, identical everywhere:

```
sbt "bench/runMain polyframes.bench.FetchData"
sbt experiments
```

The first extracts the dataset subset; the second compiles every pipeline variant, runs the measured pipelines, and writes `results.csv` and `RESULTS.md`. Expect it to take a few minutes on the reference machine described below.

### Setup, once

Common to every platform: a JDK 17 or 21 (Spark 4 supports neither Java 8 nor Java 11) and sbt. Nothing else has to be configured by hand. Running the code outside sbt requires passing the same options, which are listed there. Then:

**Linux and macOS.** Nothing else. Run `./scripts/check-env.sh` to confirm.

**Windows.** Spark has no distributed file system of its own and relies on the Hadoop libraries for file input and output, even in local mode. Two native binaries are therefore required, and Apache does not ship them:

1. create `C:\hadoop\bin`;
2. place `winutils.exe` and `hadoop.dll` for Hadoop 3.4.x in it;
3. set `HADOOP_HOME` to `C:\hadoop` and add `%HADOOP_HOME%\bin` to `PATH`;
4. keep the project at a short path without spaces, such as `C:\dev\polyframes`;
5. exclude the project folder and the `.sbt`, `.ivy2` and `coursier` caches from real-time antivirus scanning. This is not cosmetic: scanning the build    caches inflates and destabilises compilation times, which are one of the three measurements reported.

The pipelines write their results to a fresh directory under the system temporary folder rather than to a fixed path inside the repository.

Run `.\scripts\check-env.ps1` to confirm all five.

### The pipeline variants

Each of the two pipelines of the article, P1 and P2, is written three ways: with PolyFrames, with plain `DataFrame`s and with `Dataset`s. The six correct versions live in the source tree of the harness (`bench/src/main/scala/polyframes/bench/P1.scala`, `P1DataFrame.scala`, `P1Dataset.scala`, and the same three for P2).

The four families of data engineering errors the article prevents are then injected into each of the six. Some of the resulting variants are meant not to compile, so they live under `variants/` rather than in the source tree, and each is compiled in isolation, so that a variant failing to compile does not stop the others.

```
variants/
  pf-*.scala      P1 written with PolyFrames
  df-*.scala      P1 written with DataFrames
  ds-*.scala      P1 written with Datasets
  pf2-*.scala     P2, same three prefixes with a 2
  df2-*.scala
  ds2-*.scala
  width-N.scala   a declaration of N attributes, for the compilation-time axis

  *-noattr        an attribute absent from the schema
  *-badtype       an attribute of an unexpected domain
  *-badmodel      data in a model the operator does not accept
  *-badtransfo    a transformation breaking the announced model
```

`sbt experiments` runs four programs in turn, each in a JVM of its own: `CompileWidth` times the `width-N` declarations, `CompileVariants` compiles every variant and records whether it compiled and how long it took, `RunExperiments` runs the variants that compiled and records whether they fail at run time, then measures the correct pipelines at each volume, and `Report` writes `RESULTS.md`. `PROTOCOL.md` describes each of them in full.

To try one variant by hand, without timing anything or writing to `results.csv`:

```
sbt "bench/runMain polyframes.bench.CompileVariant variants/pf-noattr.scala"
```

The compiler message it prints is the guarantee, in the form a user sees it.

## Data

Open Food Facts, daily JSONL export, ODbL. See `DATA.md` for the exact dump date, the extraction procedure and the attribution notice.

## Layout

```
core/        the library: types, inference, runtime layer, operators
bench/       the measured pipelines, the baselines and the harness
variants/    standalone pipeline variants, each compiled in isolation,
             including the ones that must fail to compile
reference/   the hand-written reference table P2 integrates with
scripts/     environment checks (check-env.sh, check-env.ps1)
MAPPING.md   article rule -> Scala construct -> file
PROTOCOL.md  what the experiments measure, and how
DATA.md      where the data come from, and under which licence
results.csv  every figure the article reports
RESULTS.md   a reading of results.csv
```

## Citing

If you use PolyFrames, please cite it through the metadata in `CITATION.cff` (GitHub offers them under "Cite this repository"). Every release is archived on Zenodo : https://doi.org/10.5281/zenodo.22875856.

## Licence

The code is distributed under the GNU General Public License, version 3 only (`GPL-3.0-only`); see `LICENSE`.

The Open Food Facts data used by the experiments are not distributed with the code. They are available under the Open Database License (ODbL); see `DATA.md`.
