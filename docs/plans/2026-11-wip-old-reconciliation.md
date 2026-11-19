# DSL `wip-old` reconciliation (2026-11-19)

Source: `wip-old` 38b3c11 (two commits past common ancestor 1b2ac8d); target `main` a00ddf6. **Plan only; do not merge the historical branch.** Coordinated with `awfl-us/workflows` `docs/plans/2026-11-wip-old-integration.md`.

## Keep, rewrite, or discard

| Historical change | Decision | Justification |
|---|---|---|
| `Steps.scala` `IsAdt`, `Match.apply`; `macros/{CaseExtractor,Macros,MacrosImpl}.scala` | **Prototype / rewrite** behind tests | Useful typed branching, but macro handles only a subset of Scala patterns, reconstructs matching values and compares `json.encode_to_string` of entire values; it is not yet established that this preserves true pattern-match semantics. Avoid building higher layers on this until proven. |
| `Spec.scala` fallback `LowPrioritySpec.nonProductSpec`, `nonProductBuilder` | **Reject as-is** | May convert a missing compile-time typeclass into a runtime `sys.error`. Require explicit errors/derivation for unsupported ADTs, not silent acceptance. |
| `Value.scala` `OptBase.present`, `OptResolved.mapValue`, optional nested lookup fixes | **Selective port** after comparison | Supports optional workflow inputs and Kala; preserve main's optional-query behavior and ensure null/missing distinctions. |
| `Value.scala` `buildList`, `buildValueList` `Switch(...).fn` cleanup | **Investigate independently** | Output and step ordering must be identical; do not bundle with the macro work. |
| `_` -> `?` wildcards, metals/build version tweaks, `.awfl/config.json` | **Discard unrelated churn** | Current toolchain/versions and repository config prevail. |

## Implementation slices

1. Build a minimal standalone `Match` proof using current `dsl` and current Scala version, without adding `Spec` runtime fallback. Decide whether matching can be expressed with existing `Switch`, `Try`, `ForRange`, and `Block` steps or needs a smaller new DSL construct. This is **not** a requirement for implementing typed tools: `JsonSchema` can proceed in parallel.
2. Define exact semantics before implementation: first matching branch only; no match is a typed failure or exhaustivity error; guards/typed patterns either work or fail at compile-time; binders refer to the same selected branch; no eager evaluation of nonmatching branches; discriminant/type checking rather than full serialized-value equality.
3. Port only demonstrated useful `OptResolved` / `OptBase` fixes. Ensure optional string absent vs empty vs explicit null behaves correctly for `EventHandler.Input.query` and nested property `map.get`.
4. If the macro meets semantics, add stable AST/snapshot assertions and dedicated native execution fixtures; otherwise document rejection and retain explicit `Switch`.

## Required tests and gates

- `sbt test` against current dependencies, plus compiler warnings as failures for macro-related sources.
- Positive/negative compile tests: singleton, multiple case classes, nested typed fields, wildcard fallback, first-branch precedence, nonmatching/no-default, guards, unsupported extractor, optional/list payload, raw primitives, and each supported binder form.
- **Semantic runtime tests:** a `case SegKala(...)` must match *any instance of that subtype* and bind the actual fields; it must not require equality to a fabricated instance; `SessionKala`, `WeekKala`, and `TermKala` must route only to their matching handler. Different field values of the same subtype must still match. Compare to ordinary Scala `match` for an equivalent input set.
- Compile generated workflows with **current `compiler-rust`**; execute native Rust on both first-run and replay/resume, including error paths. No Google Workflows deployment test is required.
- Public API compatibility: existing `Step`, `Spec`, `OptValue`, `ListValue`, and CEL semantics still pass all existing DSL and downstream workflow tests.

## Stop conditions

If the macro cannot pass subtype/binder/exhaustivity tests without invasive changes, drop it from this reconciliation. Adopt only the isolated optional-value correction. No broad implicit fallback or Scala-version change is justified by `wip-old` alone.
