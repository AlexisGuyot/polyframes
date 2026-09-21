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

Some variants are meant not to compile: they carry the four families of data engineering errors the article prevents. They therefore live under `variants/` rather than in the source tree, and each is compiled in isolation, so that a variant failing to compile does not stop the others.

```
variants/
  p1-ok.scala            the reference pipeline, error free
  p1-noattr.scala        an attribute absent from the schema
  p1-badtype.scala       an attribute of an unexpected domain
  p1-badmodel.scala      data in a model the operator does not accept
  p1-badtransfo.scala    a transformation breaking the announced model
  ...                    the same four, for the DataFrame and Dataset baselines
```

`RunAll` compiles each of them, records whether compilation succeeded and how long it took, then runs the ones that compiled and records whether they fail at
run time. To try one by hand:

```
sbt "bench/runMain polyframes.bench.CompileVariant variants/p1-noattr.scala"
```

The compiler message it prints is the guarantee, in the form a user sees it.

## Data

Open Food Facts, daily JSONL export, ODbL. See `DATA.md` for the exact dump date, the extraction procedure and the attribution notice.

## Layout

```
core/      the library: types, inference, runtime layer, operators
bench/     the measured pipelines, the baselines and the harness
variants/  standalone pipeline variants, each compiled in isolation, including the ones that must fail to compile
scripts/   data extraction, compilation of the variants, evaluation
MAPPING.md article rule -> Scala construct -> file
PROTOCOL.md what the experiments measure, and how
```

## Citing

TODO Zenodo DOI

## Licence

TODO
