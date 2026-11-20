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

## Phase 2: original `IsAdt` / `Match` JSON-candidate algorithm (2026-11-20)

**Source parity:** restored `Macros`, `MacrosImpl`, `CaseExtractor`, `IsAdt`, and `Match` from `dsl/wip-old`, not the rejected experiment of generating direct `$awflType` predicates. The original `IsAdt` is only an empty marker trait. It imposes no subtype discriminator; the source algorithm reconstructs one JSON value per case using the bound input fields, protects failed candidate projections with `Try`, then uses `json.encode_to_string(in) == json.encode_to_string(candidate)` in a first-match `Switch`.

**Usage sites reviewed from `wip-old`:** `workflow-utils/Locks.scala` `buildSessionKey` uses constructor field binders (`SegKala(sessionId, _, _)`); `workflow-utils/TopicContextYoj.scala` `encodeKalaVal` uses typed binders (`case seg: SegKala` and accesses `seg.sessionId` etc.); the `Match` form of `Prakriya.read` is *commented out* and therefore not a tested active use site. Workflow `EventHandler` carries `KalaVibhaga`, but does not directly use `Match` in the old branch.

**Fixes beyond original source:**

- Import the macro explicitly and fix `ForRange[T]` / `Switch[T, BaseValue[T]]` inference under the current Scala 3.3.1 DSL. No extra Rust compiler operators, serializer tags or alternative AST are needed.
- A non-exhaustive match previously returned a **successful `{}`** in native Rust. `Match` now appends a terminal `Raise` with `Non-exhaustive ADT match: <name>` instead of concealing invalid or unmatched input. A deliberate final `case _` remains a total default.
- Preserve **literal constructor fields** when building JSON candidates. This supports matching values inside a case class, for example `case Created("admin", id)` versus `case Created("user", id)`, without changing native serialization. The existing `Spec` primitive unsafe `.get` protection remains in effect for unbound raw primitive fields.
- Preserve typed subtype-only tests (`case _: SegKala`) rather than treating them as wildcards; reject unrecognized pattern shapes at Scala macro expansion time rather than silently treating them as unconditional matches.

**Evidence:**

- DSL Scala tests `KalaMatchSmoke`, `LiteralPatternSmoke`, and existing `NonProductSpecSmoke` pass.
- The compiler-rust branch `test/native-json-candidate-match` adds two Scala-generated Kala fixtures, one source-order/wildcard fixture, one effect/suspension fixture, and one value-sensitive literal-pattern fixture. With only a temporary `dsl:0.1.0-SNAPSHOT` dependency override, the unchanged native Rust compiler generated five binary fixtures and `scripts/test_adt_match.py` passed **31 real Rust execution assertions**: 8 Kala cases, 8 malformed-input failures, 3 first-match/wildcard checks, 3 unselected effect bypasses, 1 selected HTTP effect suspension, 1 injected continuation/resume, and 7 literal value checks. No HTTP/network effect was actually sent. No Google Workflows or durable journaling.

**Explicit supported subset and remaining work:**

- Supported: case-class constructor patterns with simple `Value`/`ListValue` binders, `_` wildcard fields, typed case-class binders, subtype-only tests, literal raw primitive fields, and terminal `case _` wildcard. First-match source ordering is preserved.
- Not yet supported: guards, arbitrary recursive/nested extractors, complex literal-and-binder expressions in the same RHS (for example `case Created("admin", id) => ... str(id.cel + "@admin")` still fails with a Scala 3 quote-owner error; returning `id` itself works), and guaranteed discrimination when two different ADT subtypes serialize to **the same JSON shape**. Such identical-shape variants require an explicit encoded discriminator or a distinct serialization contract in a later compatibility-reviewed change. Do not claim that the marker `IsAdt` adds a discriminator automatically.
- The native fixture branch must not be merged against compiler-rust's production `dsl:0.3.5` until a versioned DSL artifact containing `Match` is available; the test source intentionally depends on the new DSL.
- Production still runs the prior `dsl:0.3.5` and native Rust PostgreSQL **memory mode**. No production workflow or binary has changed. Durable mode remains deferred.

**Next gate:** settle same-shape ADT representation and the Scala binder quote-owner limitation; publish a versioned DSL, run current full ProjectManager/native parity against it, then merge the Rust conformance fixtures and move on to the remaining lifecycle traits.


## ADT `Match` reconciliation — Phase 3.18 (2026-11-20)

**Decision: preserve the original `wip-old` candidate-JSON matching algorithm, not an inferred `$awflType` discriminator or tag-only optimization.** `IsAdt` is an empty marker. The original `Match` macro reconstructs the case-class-shaped candidate for every pattern, substitutes field binders, evaluates each candidate in a `ForRange` with `Try` for failed projections, and selects the *first* whose `json.encode_to_string` matches the original input. Both constructors with bound fields and `case segment: SegKala` typed-binder patterns originated in `workflow-utils/wip-old` (`Locks.scala`, `TopicContextYoj.scala`). Legacy `Prakriya.scala` has a commented-out match as an additional reference.

Imported `dsl/wip-old`'s `macros/Macros.scala`, `MacrosImpl.scala`, `CaseExtractor.scala` and `Steps.scala` API selectively. Integration changes: an explicit macro import; explicit `ForRange[T]`/`Switch` type arguments for current compiler inference; the **deferred-failure `nonProductSpec[T]`** from the preceding slice; and an additional terminal `Raise` branch, because the native `Switch` otherwise returned `{}` on no match. The macro carries *literal constructor fields* into the reconstructed candidate, unlike the old wildcard-only code. A corrected fully applied `Resolver.in/list[T](field)(spec)` quoted term resolves the existing binder-in-compound-RHS bug (`id.cel`), not just an incidental source rewrite. Constructor binders and typed case-class binders continue to work.

**Semantic boundary:** compares serialized candidate *objects* exactly, without a subtype tag. Thus different constructor shapes are distinguishable, and literal constraints are meaningful. Two variants with structurally identical JSON are not distinguishable by type alone; extending `IsAdt` does not enforce a discriminant. Existing on-wire shape is retained. Only supported case-class, simple binders/wildcards, simple literal fields, and typed case-class patterns are implemented; nested destructuring, `if` guards and unsupported AST forms must not silently compile as wildcard matches. Source order is honored. The terminal no-match error is `Non-exhaustive ADT match: <name>`, returned as native failed status.

**Tests:** local DSL `sbt test publishLocal` (only `0.1.0-SNAPSHOT`, not a real published release) passed, as did `KalaMatchSmoke` and `LiteralPatternSmoke`. `compiler-rust` `AdtMatchNativeSmoke` / `AdtLiteralNativeSmoke` generate actual Rust from unmodified native compiler production sources; `scripts/test_adt_match.py` compiles and runs the binaries in memory mode and asserts 31 cases: four Kala variants × two real-wip-old-inspired call sites, malformed nonmatches, literal field role selection including computed `id.cel + "@admin"`, first-match/wildcard order, branch-local effect isolation, and real native v2 suspension/resumption using a mock effect result (no HTTP request sent). **No compiler serialization change, Google Workflows, or durable journal.**

**Next:** publish a new DSL version only after checking downstream `workflow-utils`/Project Manager builds; then selectively wire the original Kala usage sites under tests, complete any remaining semantic boundaries deliberately, and resume `Summaries`, `ExtractTopics`, `ContextCollapser`.
