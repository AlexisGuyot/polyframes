# Data

## Source

Open Food Facts, "Products" JSONL export.

- Home: https://world.openfoodfacts.org/data
- Licence: Open Database License (ODbL). Individual contents are under the Database Contents License (DbCL).
- Dump date used for the reported measurements: see data/PROVENANCE.txt

## Attribution

Data from Open Food Facts, licensed under the Open Database License (ODbL).

## What is extracted

`polyframes.bench.FetchData` downloads the dump for the pinned date, keeps the first N products, and writes one JSONL file per volume point under `data/`, at the root of the repository. Each file is a prefix of the next.

Paths are resolved against the root of the repository, found by walking up from the working directory until a build.sbt appears, so the programs behave the same whether they are launched by sbt, by an IDE, or by hand.

Volume points: TODO
Declared schema widths: TODO
