# Correspondence: article rule -> Scala construct -> file

Every type of the article is a trait; every rule is an implicit definition in the companion object of that trait. Rule numbers refer to the submitted version of the article.

## Kernel - types (article, section 3.2)

| Article | Rules | Scala construct | File |
|---|---|---|---|
| `Key(l)` | 2 | literal singleton type (`"id"`), value via `Witness.Aux[K]` | *(none - native)* |
| `Key(l) ->> T` | 3 | `shapeless.labelled.FieldType[K, T]` | *(none - reused)* |
| `Schema`, `SNil`, `x` | 4-8 | `shapeless.HList`, `HNil`, `::` | *(none - reused)* |
| `Data[M,S]` | 9 | `final class Data` + guarded factory | `runtime/Data.scala` |
| `Model`, `Relation`, `Document` | 10-14 | `sealed trait Model`, two subtraits | `types/Model.scala` |
| `Valid[Relation,S]` | 15-16 | `implicit def relLast` / `relMore` | `types/Valid.scala` |
| `RelType[T]` | 17-18 | one `implicit val` per admissible domain | `types/Valid.scala` |
| `Valid[Document,S]` | 19-20 | `implicit def docLast` / `docMore` | `types/Valid.scala` |
| `DocType[T]` | 21-24 | one `implicit val` per atomic domain, two recursive `implicit def` | `types/Valid.scala` |

## Kernel - inference (article, section 3.3)

| Article | Rules | Scala construct | File |
|---|---|---|---|
| `Path`, `PNil`, `.` | 25-29 | `HList` of singleton types | *(none - reused)* |
| `ListOfPaths`, `LoPNil` | app. B.1 | `HList` of `HList` | *(none - reused)* |
| `ProjectOne[S,P] => NS` | 30-32 | `trait ProjectOne { type Out }` + `Aux` | `infer/Project.scala` |
| `Project[S,L] => NS` | 33-34 | `trait Project { type Out }` + `Aux` | `infer/Project.scala` |
| `AddFlat[S,K,T] => NS` | 35-36 | `trait AddFlat { type Out }` + `Aux`, `=:!=` | `infer/Add.scala` |
| `Add[S,P,T] => NS` | 37-39 | `trait Add { type Out }` + `Aux` | `infer/Add.scala` |
| `LookupFlat`, `Lookup` | app. B.2 | `trait Lookup { type Out }` + `Aux` | `infer/Lookup.scala` |
| `UpdateFlat`, `Update` | app. B.3 | `trait Update { type Out }` + `Aux` | `infer/Update.scala` |
| `Rename` | app. B.4 | `trait Rename { type Out }` + `Aux` | `infer/Rename.scala` |
| `Drop` | app. B.5 | `trait Drop { type Out }` + `Aux` | `infer/Drop.scala` |
| `Merge` | app. B.6 | `trait Merge { type Out }` + `Aux` | `infer/Merge.scala` |
| premise `LookupFlat[S, Key(l)] => List[S_sub]` (rules 32, 39, app. B) | | `LookupFlat.Aux` followed by `ListOfSchema.Aux` | `infer/ListOfSchema.scala` |

## Operators

| Article | Rule | Scala construct | File |
|---|---|---|---|
| `project[M_out, L](d)` | 40 | `def project` | `ops/Ops.scala` |
| `add[M_out, P](d, f)` | 41 | `def add` | `ops/Ops.scala` |
| `drop`, `rename`, `update`, `join` | appendix C | `def drop` etc. | `ops/Ops.scala` |

## Runtime layer (no counterpart in the article)

| Purpose | Scala construct | File |
|---|---|---|
| decode a `Row` into a term of a schema type | `trait FromRow[S]` | `runtime/FromRow.scala` |
| lay out the columns of a DataFrame in schema order | `trait Shape[S]` | `runtime/Shape.scala` |
| resolve a `Path` to a (possibly nested) column | `trait Target[P]` | `runtime/Target.scala` |
| declare a domain, or a whole schema, to Spark | `trait SparkType[T]`, `trait SparkSchema[S]` | `runtime/SparkType.scala` |
| reject paths traversing a collection, which the binding does not execute yet | `trait ObjectPath[S, P]`, `trait ObjectPaths[S, L]` | `runtime/ObjectPath.scala` |
