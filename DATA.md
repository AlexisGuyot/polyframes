# Data

## Source

Open Food Facts, "Products" JSONL export.

- Home: https://world.openfoodfacts.org/data
- Licence: Open Database License (ODbL). Individual contents are under the Database Contents License (DbCL).
- Dump used for the reported measurements: see `data/PROVENANCE.txt`

## Attribution

Data from Open Food Facts, licensed under the Open Database License (ODbL).

## What is extracted

`polyframes.bench.FetchData` streams the current dump, keeps the first N products, and writes one JSONL file per volume point under `data/`, at the root of the repository. Each file is a prefix of the next. It records in `data/PROVENANCE.txt` the URL, the licence, the `Last-Modified` header of the dump, the date of retrieval and the number of products kept.

Open Food Facts publishes only its latest export. Running `FetchData` again therefore extracts a different sample, and the figures it leads to will differ from those of the article, which were measured on the dump whose date `data/PROVENANCE.txt` records. The extracted files are not redistributed with the code; only `data/PROVENANCE.txt` is versioned.

Paths are resolved against the root of the repository, found by walking up from the working directory until a build.sbt appears, so the programs behave the same whether they are launched by sbt, by an IDE, or by hand.

Volume points: 10 000, 50 000, 100 000, 250 000 and 500 000 products.

Declared schema widths, for the compilation-time axis: 5, 10, 20, 40 and 80 attributes (`variants/width-N.scala`).

The reference table `reference/nutriscore-grades.csv`, which P2 integrates with, is not extracted from the dump. It was written by hand, and is versioned with the code.
