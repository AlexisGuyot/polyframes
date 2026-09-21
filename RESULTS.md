# Results

Produced by `sbt experiments`, then `sbt "bench/runMain polyframes.bench.Report"`. Every figure below is a row of `results.csv` and nothing else. The machine, the pinned versions and the extracted data are described in `README.md` and `data/PROVENANCE.txt`.

## Where each error is caught, P1

| error | polyframes | dataframe | dataset |
|---|---|---|---|
| an attribute absent from the schema | compile time | run time | compile time |
| an attribute of an unexpected domain | compile time | run time | compile time |
| data in an unexpected model | compile time | run time | run time |
| a transformation breaking the announced model | compile time | run time | run time |

## Where each error is caught, P2

| error | polyframes | dataframe | dataset |
|---|---|---|---|
| an attribute absent from the schema | compile time | run time | compile time |
| an attribute of an unexpected domain | compile time | run time | compile time |
| data in an unexpected model | compile time | run time | run time |
| a transformation breaking the announced model | compile time | run time | run time |

## Compiling the correct pipelines

| pipeline | compilation (ms) |
|---|---|
| p1 | 1625 |
| p1-dataframe | 337 |
| p1-dataset | 959 |
| p2 | 498 |
| p2-dataframe | 87 |
| p2-dataset | 396 |

## Running the correct pipelines

| products | p1 ms | p1 MB | p1-dataframe ms | p1-dataframe MB | p1-dataset ms | p1-dataset MB | p2 ms | p2 MB | p2-dataframe ms | p2-dataframe MB | p2-dataset ms | p2-dataset MB |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 10000 | 770 | 198 | 766 | 247 | 659 | 378 | 710 | 525 | 581 | 259 | 743 | 329 |
| 50000 | 1234 | 553 | 1205 | 694 | 1244 | 755 | 1368 | 808 | 1207 | 765 | 1408 | 813 |
| 100000 | 2773 | 454 | 2835 | 792 | 2779 | 829 | 2919 | 910 | 2670 | 719 | 2914 | 1045 |
| 250000 | 6346 | 976 | 6500 | 810 | 6350 | 1058 | 6809 | 1082 | 6207 | 976 | 6487 | 1057 |
| 500000 | 14129 | 1472 | 13521 | 903 | 13618 | 935 | 13721 | 1078 | 13214 | 1116 | 13914 | 1065 |

## Compiling a declaration of growing width

| declared attributes | compilation (ms) |
|---|---|
| 5 | 752 |
| 10 | 735 |
| 20 | 849 |
| 40 | 1542 |
| 80 | 10258 |

## What each baseline reports, and when

| variant | failure |
|---|---|
| df-noattr | ExtendedAnalysisException: [UNRESOLVED_COLUMN.WITH_SUGGESTION] A column, variable, or function parameter with name `cod` |
| df-badtype | SparkNumberFormatException: [CAST_INVALID_INPUT] The value 'e' of the type "STRING" cannot be cast to "DOUBLE" because i |
| df-badmodel | AnalysisException: [UNSUPPORTED_DATA_TYPE_FOR_DATASOURCE] The CSV datasource doesn't support the column `countries_tags` |
| df-badtransfo | AnalysisException: [UNSUPPORTED_DATA_TYPE_FOR_DATASOURCE] The CSV datasource doesn't support the column `nutriments` of  |
| ds-badmodel | AnalysisException: [UNSUPPORTED_DATA_TYPE_FOR_DATASOURCE] The CSV datasource doesn't support the column `countries_tags` |
| ds-badtransfo | AnalysisException: [UNSUPPORTED_DATA_TYPE_FOR_DATASOURCE] The CSV datasource doesn't support the column `nutriments` of  |
| df2-noattr | ExtendedAnalysisException: [UNRESOLVED_COLUMN.WITH_SUGGESTION] A column, variable, or function parameter with name `grad |
| df2-badtype | SparkNumberFormatException: [CAST_INVALID_INPUT] The value 'Bad nutritional quality' of the type "STRING" cannot be cast |
| df2-badmodel | AnalysisException: [UNSUPPORTED_DATA_TYPE_FOR_DATASOURCE] The CSV datasource doesn't support the column `countries_tags` |
| df2-badtransfo | AnalysisException: [UNSUPPORTED_DATA_TYPE_FOR_DATASOURCE] The CSV datasource doesn't support the column `nutriments` of  |
| ds2-badmodel | AnalysisException: [UNSUPPORTED_DATA_TYPE_FOR_DATASOURCE] The CSV datasource doesn't support the column `countries_tags` |
| ds2-badtransfo | AnalysisException: [UNSUPPORTED_DATA_TYPE_FOR_DATASOURCE] The CSV datasource doesn't support the column `nutriments` of  |

## Reading these figures

Compilation is timed in process, on a warm JVM, and the first run of each file is discarded. The figures are therefore lower than what a user waiting on a cold build would see, and are meant for comparing the variants with one another.

Memory is sampled, not computed: a thread reads the used heap every 50 ms and the highest reading is kept. It sees the whole virtual machine, not the pipeline alone.