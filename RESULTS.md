# Results

Produced by `sbt experiments`, then `sbt "bench/runMain hetframes.bench.Report"`.
Every figure below is a row of `results.csv` and nothing else.
The machine, the pinned versions and the extracted data are described in
`README.md` and `data/PROVENANCE.txt`.

## Where each error is caught, P1

| error | hetframes | dataframe | dataset |
|---|---|---|---|
| an attribute absent from the schema | compile time | run time | compile time |
| an attribute of an unexpected domain | compile time | run time | compile time |
| data in an unexpected model | compile time | run time | run time |
| a transformation breaking the announced model | compile time | run time | run time |

## Where each error is caught, P2

| error | hetframes | dataframe | dataset |
|---|---|---|---|
| an attribute absent from the schema | compile time | run time | compile time |
| an attribute of an unexpected domain | compile time | run time | compile time |
| data in an unexpected model | compile time | run time | run time |
| a transformation breaking the announced model | compile time | run time | run time |

## Compiling the correct pipelines

| pipeline | compilation (ms) |
|---|---|
| p1 | 326 |
| p1-dataframe | 89 |
| p1-dataset | 227 |
| p2 | 545 |
| p2-dataframe | 94 |
| p2-dataset | 264 |

## Running the correct pipelines

| products | p1 ms | p1 MB | p1-dataframe ms | p1-dataframe MB | p1-dataset ms | p1-dataset MB | p2 ms | p2 MB | p2-dataframe ms | p2-dataframe MB | p2-dataset ms | p2-dataset MB |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 10000 | 566 | 239 | 510 | 276 | 548 | 237 | 650 | 298 | 625 | 254 | 668 | 296 |
| 50000 | 1147 | 425 | 1071 | 452 | 1241 | 741 | 1278 | 484 | 1049 | 456 | 1060 | 782 |
| 100000 | 2635 | 915 | 2594 | 623 | 2623 | 1007 | 2787 | 697 | 2571 | 699 | 2779 | 631 |
| 250000 | 6125 | 1112 | 5955 | 773 | 6105 | 1022 | 6072 | 1093 | 5783 | 864 | 6095 | 1015 |
| 500000 | 14642 | 933 | 13769 | 934 | 13583 | 963 | 16001 | 1061 | 16508 | 1021 | 14473 | 1007 |

## Compiling a declaration of growing width

| declared attributes | compilation (ms) |
|---|---|
| 5 | 137 |
| 10 | 194 |
| 20 | 415 |
| 40 | 1614 |
| 80 | 11158 |

## What each baseline reports, and when

| variant | failure |
|---|---|
| df-noattr | ExtendedAnalysisException: [UNRESOLVED_COLUMN.WITH_SUGGESTION] A column, variable, or function parameter with name `cod` |
| df-badtype | SparkNumberFormatException: [CAST_INVALID_INPUT] The value 'high' of the type "STRING" cannot be cast to "DOUBLE" becaus |
| df-badmodel | AnalysisException: [UNSUPPORTED_DATA_TYPE_FOR_DATASOURCE] The CSV datasource doesn't support the column `countries_tags` |
| df-badtransfo | AnalysisException: [UNSUPPORTED_DATA_TYPE_FOR_DATASOURCE] The CSV datasource doesn't support the column `nutriments` of  |
| ds-badmodel | AnalysisException: [UNSUPPORTED_DATA_TYPE_FOR_DATASOURCE] The CSV datasource doesn't support the column `countries_tags` |
| ds-badtransfo | AnalysisException: [UNSUPPORTED_DATA_TYPE_FOR_DATASOURCE] The CSV datasource doesn't support the column `nutriments` of  |
| df2-noattr | ExtendedAnalysisException: [UNRESOLVED_COLUMN.WITH_SUGGESTION] A column, variable, or function parameter with name `grad |
| df2-badtype | SparkNumberFormatException: [CAST_INVALID_INPUT] The value 'high' of the type "STRING" cannot be cast to "DOUBLE" becaus |
| df2-badmodel | AnalysisException: [UNSUPPORTED_DATA_TYPE_FOR_DATASOURCE] The CSV datasource doesn't support the column `countries_tags` |
| df2-badtransfo | AnalysisException: [UNSUPPORTED_DATA_TYPE_FOR_DATASOURCE] The CSV datasource doesn't support the column `nutriments` of  |
| ds2-badmodel | AnalysisException: [UNSUPPORTED_DATA_TYPE_FOR_DATASOURCE] The CSV datasource doesn't support the column `countries_tags` |
| ds2-badtransfo | AnalysisException: [UNSUPPORTED_DATA_TYPE_FOR_DATASOURCE] The CSV datasource doesn't support the column `nutriments` of  |

## Reading these figures

Compilation is timed in process, on a warm JVM: every file is compiled
once unmeasured, then in interleaved rounds, and the median of the
rounds is kept. The figures are therefore lower than what a user
waiting on a cold build would see, and are meant for comparing the
variants with one another. Execution follows the same protocol.

Memory is sampled, not computed: a thread reads the used heap every
50 ms and the highest reading is kept. It sees the whole virtual
machine, not the pipeline alone.
