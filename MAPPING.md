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
| `DocType[T]` | 21-24 | four `implicit def`, two recursive | `types/Valid.scala` |

## Kernel - inference (article, section 3.3)

| Article | Rules | Scala construct | File |
|---|---|---|---|
| `Path`, `PNil`, `.` | 30-34 | `HList` of singleton types | *(none - reused)* |
| `ListOfPaths`, `LoPNil` | app. B.1 | `HList` of `HList` | *(none - reused)* |
| `ProjectOne[S,P] => NS` | 35-37 | `trait ProjectOne { type Out }` + `Aux` | `infer/Project.scala` |
| `Project[S,L] => NS` | 38-39 | `trait Project { type Out }` + `Aux` | `infer/Project.scala` |
| `AddFlat[S,K,T] => NS` | 40-41 | `trait AddFlat { type Out }` + `Aux`, `=:!=` | `infer/Add.scala` |
| `Add[S,P,T] => NS` | 42-44 | `trait Add { type Out }` + `Aux` | `infer/Add.scala` |
| `LookupFlat`, `Lookup` | app. B.2 | `trait Lookup { type Out }` + `Aux` | `infer/Lookup.scala` |
| `UpdateFlat`, `Update` | app. B.3 | `trait Update { type Out }` + `Aux` | `infer/Update.scala` |
| `Rename` | app. B.4 | `trait Rename { type Out }` + `Aux` | `infer/Rename.scala` |
| `Drop` | app. B.5 | `trait Drop { type Out }` + `Aux` | `infer/Drop.scala` |
| `Merge` | app. B.6 | `trait Merge { type Out }` + `Aux` | `infer/Merge.scala` |

## Operators

| Article | Rule | Scala construct | File |
|---|---|---|---|
| `project[L, M_out](d)` | 45 | `def project` | `ops/Ops.scala` |
| `add[P, M_out](d, f)` | 46 | `def add` | `ops/Ops.scala` |
| `drop`, `rename`, `update`, `join` | appendix C | `def drop` etc. | `ops/Ops.scala` |

## Runtime layer (no counterpart in the article)

| Purpose | Scala construct | File |
|---|---|---|
| decode a `Row` into a term of a schema type | `trait FromRow[S]` | `runtime/FromRow.scala` |
| resolve a `Path` to a (possibly nested) column | `trait Target[P]` | `runtime/Target.scala` |
| build a schema type from a JSON sample | `object SchemaOf` | `runtime/SchemaOf.scala` |
