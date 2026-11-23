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
| p1 | 342 |
| p1-dataframe | 104 |
| p1-dataset | 252 |
| p2 | 571 |
| p2-dataframe | 104 |
| p2-dataset | 244 |

## Running the correct pipelines

| products | p1 ms | p1 MB | p1-dataframe ms | p1-dataframe MB | p1-dataset ms | p1-dataset MB | p2 ms | p2 MB | p2-dataframe ms | p2-dataframe MB | p2-dataset ms | p2-dataset MB |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 10000 | 578 | 232 | 490 | 285 | 569 | 313 | 688 | 292 | 621 | 289 | 704 | 309 |
| 50000 | 1030 | 397 | 815 | 424 | 865 | 447 | 967 | 653 | 815 | 401 | 1004 | 965 |
| 100000 | 1862 | 1022 | 1827 | 616 | 1913 | 646 | 1984 | 926 | 1779 | 665 | 2043 | 686 |
| 250000 | 4210 | 1265 | 3991 | 819 | 4038 | 1074 | 4192 | 1161 | 3821 | 843 | 4143 | 1169 |
| 500000 | 10509 | 1013 | 8891 | 931 | 8848 | 960 | 9748 | 1174 | 11838 | 1017 | 9601 | 1012 |

## Compiling a declaration of growing width

| declared attributes | compilation (ms) |
|---|---|
| 5 | 149 |
| 10 | 203 |
| 20 | 481 |
| 40 | 1699 |
| 80 | 11131 |

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
