# DSL `wip-old` integration — current checkpoint (2026-11-20)

**Priority order corrected:** reconcile the foundation in `awfl-us/dsl` before further `Summaries`, `ExtractTopics` or `ContextCollapser` trait restructuring. Use current `main` as the source and `wip-old` for design intent. Do not merge the old branch directly.

## Keep the deliberate non-product `Spec` exception

The original `wip-old` `Spec.scala` has two independent safety mechanisms:

1. `Spec.init(resolver)` rejects `resolver.safe=false` for primitive (unsafe) field access. This is already present on `main` and was introduced in `172e46a` (`unsafe specs`).
2. `auto.nonProductSpec[T]` supplies a **low-priority** `Spec[T]` when `Mirror.ProductOf[T]` is not available. It permits opaque ADT values to be represented as `Value[T]`, `OptValue[T]` or `ListValue[T]`, which is necessary for the original `Match[T <: IsAdt]` use sites. If `.get` actually tries to construct a non-product `T`, the builder raises `No Spec for non-product type... Use Match to branch on the actual sub-type` rather than silently constructing a false subtype. This is an intentional deferred error **during DSL step construction in Scala**, not native runtime subtype checking. Reversing the previous blanket rejection of this fallback.

Implemented the original low-priority `nonProductSpec` idea in `src/main/scala/us/awfl/dsl/Spec.scala` without adding the old `nonProductBuilder`: once `auto.nonProductSpec[T]` exists, `specBasedBuilder[T]` already uses it for nested non-product fields. The extra `NotGiven[Spec[T]]` guard in the historical builder is redundant under the same `auto.given` import. Regression `NonProductSpecSmoke`: abstract `Value[ADT]` and `ListValue[ADT]` compile; `Value[ADT].get` and a product containing an eager ADT field fail with the expected message; a product containing a wrapped `Value[ADT]` succeeds; primitive unsafe access still throws the original message.

## Optional-value corrections

Ported the narrow `OptBase.present` and `OptResolved.mapValue` / nested `map.get` changes from `wip-old` into `Value.scala`, preserving current `getOrElse` and `map` APIs and keeping missing/null/default handling explicit. Regression `OptionalValueSmoke` checks generated CEL AST for direct defaults, absent nested paths, mapped nested values, and presence checks. No general JSON/schema/typeclass changes, build version churn or original `buildList` cleanup were imported.

## Local validation

- `sbt -batch 'Test/compile' 'Test/runMain us.awfl.dsl.OptionalValueSmoke' 'Test/runMain us.awfl.dsl.NonProductSpecSmoke'` — PASS.
- `sbt -batch test publishLocal` — PASS; **local-only** artifact `us.awfl:dsl_3:0.1.0-SNAPSHOT`. This does not publish a production `0.3.6` release.
- `workflow-utils`: `sbt -batch 'set dependencyOverrides += "us.awfl" %% "dsl" % "0.1.0-SNAPSHOT"' compile test` — PASS, 33 Scala sources plus 2 test sources.
- `workflows`: same temporary override, `compile` — PASS, 58 Scala sources including restored Recursion/Status. No repo build definition was changed or committed for these overrides.

## Remaining gates and next slice

1. Rewrite the original `IsAdt`/`Match` macro to use true subtype discriminants and selected-branch binders instead of serialized value equality and fabricated cases; test first-match precedence, wrong subtype, no default, typed patterns, and nested fields.
2. Compile and execute the rewritten matcher on the current **native Rust** compiler, including failure paths and optional fields. Do not rely on the original unverified `KalaMatchSpec` alone (it asserts step names, not runtime semantics).
3. Run native ProjectManager parity (tool continuation, status, callback, background Striders) and cache checks using the modified DSL before a new public version is released. Current deployed `dsl` remains 0.3.5 until versioned publishing. Do not switch to Google Workflows or durable mode.
4. Only after the foundation passes, resume the independent workflow lifecycle traits.

**Execution target:** native Rust PostgreSQL **memory mode**. Durable per-effect journaling remains intentionally deferred for latency.
