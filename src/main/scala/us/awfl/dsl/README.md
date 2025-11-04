# DSL Module

This package defines the internal Domain-Specific Language (DSL) used to model and construct composable, declarative workflows. It provides expressive, type-safe building blocks for transforming YAML workflow definitions into executable structures in Scala.

## Overview
The DSL is centered around the following core abstractions:
- **Steps**: Executable units of logic
- **Values**: References to inputs, outputs, and derived expressions
- **CEL Expressions**: Conditional logic and computation
- **Specs**: Declarative contracts for type-safe resolution and binding

## File Breakdown

### `Cel.scala`
Defines a typed AST for CEL (Common Expression Language) expressions including literals, operations, function calls, and paths. It provides:
- Sealed trait `Cel` and case classes like `CelConst`, `CelOp`, `CelFunc`
- Implicit conversions from literals (String, Int, Boolean) and `BaseValue[_]` to `Cel`
- Operator extensions via `CelOps`, including ===, !==, >, <, &&, ||, etc.

### `Spec.scala`
Provides a generic `Spec[T]` type that defines how to resolve a value of type `T` from an environment (`Resolver`). Key features:
- Implicit specs for primitive and nested types
- Inline derivation of `Spec[T]` for case classes using Scala 3 `Mirror`
- `SpecBuilder` type class to construct field bindings for structured types

### `Steps.scala`
Defines all supported workflow step types. Each step extends the trait `Step[T, V]`, where `T` is the raw result type and `V` is a `BaseValue[T]`. Step types include:
- `Call`, `Return`, `Raise`, `Log`: basic operations
- `For`, `ForRange`, `Fold`: collection processing
- `Switch`, `Try`, `FlatMap`, `Block`: control flow and composition
- Many step constructors return a result value via `init()` and support chaining via `flatMap`

### `Value.scala`
Defines the value system for the DSL:
- `BaseValue[T]`: an abstract representation of a value
- `Resolved[T]`: values anchored to a `Resolver` path
- `Value[T]`, `ListValue[T]`, `Obj[T]`: concrete value types
- `Resolver`: used to construct paths to data within the workflow context
- Built-in ops like `str(cel)`, `encodeJson()`, `len()`

## Developer Notes
- All DSL components are composable and immutable
- `Spec` and `Value` form the backbone of type-safe resolver-based value construction
- Steps are designed to be serializable to YAML-compatible data structures
- CEL support is embedded in most constructs via `Cel` references and expressions

## Example Usage
Below are some concrete examples derived from BusinessAnalysis.scala that demonstrate how the DSL is used in practice:

### Step: Calling an external service
```scala
val generateKeywords = post[StepParams, GenerateKeywordsResult](
  name = "generateKeywords",
  url = "business-report/generate-keywords",
  args = obj(StepParams(input.placeId))
).flatMap(_.body)
```

### Step: Caching a computation
```scala
def cacheReviews(name: String, placeId: PlaceId) = utils.Cache(
  name = s"cacheReviews_$name",
  collection = str("businesses.reviews"),
  id = placeId,
  thresholdMillis = 86400000,
  step = post[StepParams, Reviews](
    s"scrapeReviews_$name",
    "business-report/scrape-reviews",
    obj(StepParams(placeId))
  ).flatMap(_.body)
)
```

### Looping with For
```scala
val competitorReviews = For[Competitor, Reviews](
  name = "scrapeReviewsForCompetitors",
  in = generateKeywords.result.competitors,
  each = competitor => {
    val cache = cacheReviews("competitorReviews", competitor.get.placeId)
    List(cache) -> cache.resultValue
  }
)
```

### Constructing a multi-part prompt
```scala
val buildPrompt = buildList("buildPrompt", List(
  services.Llm.ChatMessage("system", obj("...instructions...")),
  services.Llm.ChatMessage("user", str(
    ("\nTarget Business:\n": Cel) + encodeJson(generateKeywords.resultValue) +
    "\nReviews:\n" + encodeJson(reviews.resultValue.flatMap(_.reviews)) +
    "\nCompetitors:\n" + encodeJson(zipCompetitorReviews.resultValue)
  ))
))
```

### Invoking a chat model with structured output
```scala
val analyzeReport = services.Llm.chatJson[AnalysisResponse](
  name = "analyze_business",
  prompt = buildPrompt.resultValue
)
```

---
This README is generated from actual source inspection and includes concrete examples. Update it alongside DSL changes to maintain accuracy.