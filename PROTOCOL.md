# Experimental protocol

This file describes precisely what the experiments of the article measure and how, down to the details the article itself leaves out. It is meant to be read
alongside `results.csv`, which holds every figure the article reports, and `RESULTS.md`, which is a reading of that file.

Reproducing everything:

```
sbt "bench/runMain polyframes.bench.FetchData"
sbt experiments
```

The second command is an alias for four programs run in turn, each in a JVM of its own.

## 1. What is measured

### The two pipelines

**P1** prepares a table of nutritional indicators from the raw product documents, in six steps, using only the two operators the body of the article defines.

| step | operator | target | model after |
|---|---|---|---|
| 1 | `add` | `nutriments.carb_share` | Document |
| 2 | `add` | `is_beverage` | Document |
| 3 | `project` | `code`, `nutriscore_grade`, `is_beverage`, `nutriments.carb_share` | Document |
| 4 | `add` | `dense` | Document |
| 5 | `project` | `code`, `nutriscore_grade`, `is_beverage`, `dense` | Relation |
| 6 | `add` | `flag` | Relation |

**P2** integrates the same documents with a relational reference table, in seven steps, using the whole palette.

| step | operator | target | model after |
|---|---|---|---|
| 1 | `join` | products on `nutriscore_grade`, grades on `grade_code` | Document |
| 2 | `drop` | `nutriments.salt` | Document |
| 3 | `rename` | `code` to `barcode` | Document |
| 4 | `update` | `grade_rank`, from `Long` to `Double` | Document |
| 5 | `add` | `nutriments.carb_share` | Document |
| 6 | `add` | `well_rated` | Document |
| 7 | `project` | `barcode`, `grade_label`, `grade_rank`, `well_rated` | Relation |

Both end by writing CSV. `Sink.typed` requires its operand to be relational and says so at compile time; `Sink.untyped` and `Sink.ofDataset` accept anything and leave Spark to discover the problem.

### The three ways of writing them

- `Data`, the layer of the article, in `P1.scala` and `P2.scala`.
- `DataFrame`, in `P1DataFrame.scala` and `P2DataFrame.scala`. Every attribute   is designated by a string.
- `Dataset`, in `P1Dataset.scala` and `P2Dataset.scala`. Each step is a `map` over a case class, which is the strongest form this baseline admits: writing   it with `withColumn` and a cast back to a case class would have been shorter and would have forfeited the guarantee `Dataset`s exist to provide. The price   is visible in the files: six steps, nine case classes, and every attribute carried through copied by hand into each of them.

Both baselines read with an explicit schema rather than an inferred one, for the same reason the typed version does: a product record has 2231 attributes at its root, and letting the reader discover them would make the comparison a measurement of schema inference.

### The four families of errors

Injected at the same step of each pipeline, for each of the three baselines, giving 24 erroneous programs alongside the 6 correct ones. All live under `variants/`, outside any source directory, because a third of them must not compile.

| family | injected as, in P1 | injected as, in P2 |
|---|---|---|
| attribute absent | project `cod` | project `grade_labell` |
| unexpected domain | `carb_share` created as `String` at step 1, read as a number at step 4 | `grade_rank` updated to `String` at step 4, read as a number at step 6 |
| unexpected model | the sink is reached with document data | idem, the nesting having survived the join |
| non-conforming transformation | the last projection keeps a nested attribute yet announces `Relation` | idem |

The unexpected-domain variants are deliberately the ones that put distance between the mistake and its symptom: two operators in P1, and in P2 a join, that is, a schema no declaration in the source states.

### The width axis

`variants/width-{5,10,20,40,80}.scala` run the same three steps - a read, one augmentation, one projection - over declared schemas of growing width. Every attribute is one observed in the sample, and each schema extends the previous, so the axis widens a single declaration rather than substituting five. Domains are interleaved so that no width is homogeneous in type, which would make the proof trees unrepresentatively uniform.

## 2. How it is measured

### Compilation

The harness embeds the Scala compiler as a library and compiles one source file at a time against the classpath of the JVM running it, which sbt has already populated. Two reasons for not launching sbt once per file: it would measure sbt's own startup and dependency resolution as well as the compilation, and a file that must fail would stop the others.

The price is that the compiler runs on a warm JVM, so the figures are lower than what a user waiting on a cold build sees. They are for comparing programs with one another, not for stating absolute costs. The first compilation of each file is discarded and the median of the rest is kept.

### Execution and memory

Same protocol: five runs, first discarded, median kept. Each run opens its own `SparkSession` in local mode and writes its result, which is what forces the
pipeline to run.

Memory is sampled, not computed. A thread reads `totalMemory - freeMemory` every 50 ms and the highest reading is kept. This is a coarse instrument: it sees the whole JVM rather than the pipeline, and what it reports depends on when the collector happens to run.

### Volumes

`FetchData` streams the gzipped JSONL dump, decompresses on the fly and closes the connection once it has enough lines, so only the head of a multi-gigabyte file travels. It writes one file per volume point, each a prefix of the next, so that the volume axis grows one sample rather than drawing five independent ones. `data/PROVENANCE.txt` records the URL, the licence, the `Last-Modified` header of the dump, the date of retrieval and the number of products kept.

## 3. Why the phases are separated

Three findings, each arrived at by getting it wrong first. They are the reason the protocol has the shape it has, and they are worth stating because none is visible in the results.

**Compilation and execution cannot share a process.** A fresh `Global` - the Scala compiler with its symbol table, indexed over a classpath holding all of Spark - is instantiated for every file measured. The heap left behind by a hundred of them is not a state in which the memory a pipeline requires can be read. Measured together, the same pipeline at the same volume gave 2593 ms in one phase and 1459 ms in another.

**The two compilation phases cannot share one either.** Left to accumulate, those instances fill any heap and the process spends its time collecting garbage rather than compiling. The compiler is now closed explicitly after each file and a collection forced, and the width axis is measured first and alone.

**Outputs go to a fresh directory each time, outside the repository.** Spark clears an output directory before writing to it, and on Windows that clearing
fails on files a previous run left behind, so a fixed path makes every measurement after the first fail.

## 4. Two guards against measuring the wrong thing

The harness was twice a source of the errors it was meant to observe: once a class loader, once a directory that could not be cleared. On both occasions every erroneous variant failed, which is what was expected of them, for a reason that had nothing to do with the error each carried. A table right for the wrong reason is worse than one that is wrong.

**A preflight.** Before anything is measured, a two-line file referencing the library is compiled, and a pipeline known to work is compiled, loaded and run through the very same path the variants take. If either fails, the harness stops and says so.

**A check on the failures themselves.** `Report` flags any failure message shared by variants of *different families*. Variants of one family are meant to fail alike and do - every `badmodel` variant hits the same multi-valued attribute at the sink, whichever API wrote it. One message shared across families would mean the harness broke, since four different mistakes have no reason to produce one failure.

## 5. What the figures do not establish

- Whether the injected errors are the errors that occur in practice. What is  established is that, for each family, an instance survives compilation with   either baseline and does not with the proposed layer.
- How the approach behaves on a cluster. Everything here is local mode on one machine.
- Anything about memory, for the reasons given above.
- That the declared schema describes the data. The type system checks the declared schema against the announced model, and the pipeline against the declared schema. Nothing checks the declaration against the file yet.

## 6. Where each figure comes from

`results.csv` has one row per measurement:

```
experiment,subject,baseline,family,volume,width,metric,value,note
```

| experiment | metric | reported in the article as |
|---|---|---|
| `error-prevention` | `compiles`, `runs` | the table of when each error is caught |
| `error-prevention` | `compile-ms` | - |
| `volume` | `run-ms`, `heap-mb` | the table of execution time and peak heap |
| `width` | `compile-ms` | the table of compilation against declared width |

The compilation times of the six correct pipelines are the `compile-ms` rows of the `error-prevention` experiment whose `family` is `none`.
