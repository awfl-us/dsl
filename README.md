# dsl — Workflow DSL for Scala 3

![Scala](https://img.shields.io/badge/Scala-3.3.1-red?logo=scala)
[![Maven Central](https://img.shields.io/maven-central/v/us.awfl/dsl_3?label=latest)](https://central.sonatype.com/artifact/us.awfl/dsl_3)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Status](https://img.shields.io/badge/status-alpha-blue)

A compact, type-safe DSL for describing workflows as pure data in Scala 3. Compose typed values, CEL expressions, and declarative steps to build readable, testable pipelines you can interpret any way you like.

- Strongly-typed values and specs that map to your domain models
- Composable CEL expressions for selection, math, and string ops
- Declarative step graph for calls, loops, folds, branching, and error handling
- Pure description: interpret to JSON/YAML, send to an engine, or run in your own runtime

---

## Table of contents
- [Features](#features)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Concepts at a glance](#concepts-at-a-glance)
- [Usage](#usage)
  - [Values](#values)
  - [CEL](#cel)
  - [Steps](#steps)
- [Step catalog (overview)](#step-catalog-overview)
- [End-to-end example](#end-to-end-example)
- [Releases and versioning](#releases-and-versioning)
- [Development](#development)
- [Contributing](#contributing)
- [License](#license)

---

## Features
- Type-safe Value and ListValue abstractions backed by Resolvers and Specs
- CEL expressions via operators and helpers (===, &&, +, len, encodeJson, …)
- Rich step set: Call, Log, Return, Raise, For, ForRange, Fold, Switch, Try, Block, FlatMap
- Optional values/lists with defaults (OptValue, OptList)
- Utilities for list building and composition (buildList, buildValueList, join, joinSteps)

## Installation
SBT coordinates:

- Organization: us.awfl
- Module: dsl
- Scala: 3.3.1
- Version: see the badge above for the latest release

In build.sbt:

```scala
libraryDependencies += "us.awfl" %% "dsl" % "0.1.0-SNAPSHOT"
```

If building locally first:

```bash
sbt publishLocal
```

Then in your project:

```scala
resolvers += Resolver.mavenLocal
```

## Quick start
```scala
import us.awfl.dsl._
import us.awfl.dsl.CelOps._       // operators and conversions to build CEL
import us.awfl.dsl.auto.given     // derive Spec for case classes

// Define a model using typed Values
case class User(name: Value[String])

// Anchor inputs at a root resolver (e.g., "input")
val user: Value[User] = init[User]("input")
val name: Value[String] = user.flatMap(_.name)

// Build a small program that logs and produces a list
val greetings: ListValue[String] = ForRange[String]("greetings", from = 0, to = 3) { i =>
  val msg = str(("Hello, ": Cel) + name + " #" + i)
  List(Log("log_msg", msg)) -> msg
}.resultValue

val program = Block("example", List(Log("log_user", name)) -> greetings)
```

## Concepts at a glance
- Value[T]: typed handle pointing at data (input, intermediate, or computed)
- ListValue[T]: a Value representing a list of T, indexable with a CEL index
- Resolver: a path inside the workflow context used to bind/resolve Values
- CEL: expression language used in conditions, math, selections, and derived values
- Step[T, V <: BaseValue[T]]: unit of work that produces a V (Value[T] or ListValue[T])

## Usage
### Values
- Anchoring: `init[T: Spec](name)` creates a root-anchored Value[T]
- Base types:
  - BaseValue[T]: common supertype with `.get` and `.cel`
  - Value[T]: resolved value at a Resolver path
  - ListValue[T]: resolved list; index via `list(iCel)` to get `Value[T]`
  - Obj[T]: literal inlined object/value; build with `obj(value)`
  - Field: a string field reference at the current Resolver
- Resolvers:
  - `resolver.in[T]("field")` nests a value
  - `resolver.list[T]("items")` nests a list
  - `Resolver("input")` creates a named root
- Case class navigation:
```scala
case class User(name: Value[String], id: Value[String])
val user = init[User]("input")
val name: Value[String] = user.flatMap(_.name)
```
- Literals and derived values:
  - `obj(42)` produces a literal `BaseValue[Int]`
  - `Value[T](cel: Cel)` wraps CEL as a `Value[T]`
  - `str(cel: Cel)` creates a `Value[String]` from CEL
- Optional values/lists with defaults:
```scala
val maybeName = OptValue[String](user.resolver.in[String]("nickname")).getOrElse(name)
val safeList  = OptList[String](Resolver("input").list[String]("tags")).getOrElse(ListValue.empty[String])
```

### CEL
Build CEL by combining Values, literals, and operators from `CelOps`.
- Operators: `===, !==, >, >=, <, <=, &&, ||, +, -, *, /, %`
- Helpers: `len(list)`, `encodeJson(value)`
- Turn a CEL expression into a Value:
```scala
val greeting: Value[String] = str(("Hello, ": Cel) + name)
```

### Steps
Steps produce values you can thread into later steps.
- Compose with `resultValue` or via `flatMap` on Value-producing steps
- Group and return with `Block`

## Step catalog (overview)
- Call[In, Out]
  - External invocation.
  - Example:
    ```scala
    case class Params(id: Value[String])
    case class Response(body: Value[String])
    val req = obj(Params(id = user.flatMap(_.id)))
    val fetch = Call[Params, Response]("fetchUser", "service.user.fetch", req)
    val body: Value[String] = fetch.flatMap(_.body)
    ```
- Log
  - Convenience around Call to write a log line.
  - `val log = Log("greet", str(("Hello ": Cel) + name))`
- Return[T]
  - Model returning from the current scope/pipeline.
- Raise
  - Raise an Error with message and code.
- For[In, Out]
  - Map over a `ListValue[In]` to produce a `ListValue[Out]`.
- ForRange[Out]
  - Index-based loop from `from` (inclusive) to `to` (exclusive).
- Fold[B, T]
  - Reduce a list into a single accumulator of type `B`.
- Switch[T, V <: BaseValue[T]]
  - Choose the first matching case based on CEL conditions.
- Try[T, V <: BaseValue[T]]
  - Run a block and handle failures; `except` receives a typed Error value.
- Block[T, V]
  - Group steps and explicitly surface an output value.
- FlatMap
  - Provided by `ValueStep` to chain transformations.

For more examples, see the deep dive below and the code snippets in this README.

## End-to-end example
```scala
import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import us.awfl.dsl.auto.given

case class User(name: Value[String])
val user = init[User]("input")
val name = user.flatMap(_.name)

// Log once and build three greetings
val greetings: ListValue[String] = ForRange[String]("greetings", 0, 3) { i =>
  val msg = str(("Hello, ": Cel) + name + " #" + i)
  List(Log("log_msg", msg)) -> msg
}.resultValue

val program = Block("example", List(Log("log_user", name)) -> greetings)
```

## Releases and versioning
This repo automatically tags and publishes on every merge to the default branch.

- Version source: sbt-dynver derives versions from Git tags (vX.Y.Z). Non-tag builds publish SNAPSHOTs with -SNAPSHOT.
- Tagging and GitHub Releases: semantic-release runs on pushes to main/master and creates a new vX.Y.Z tag and GitHub Release based on conventional commits since the last release.
- Publishing: sbt-ci-release publishes snapshots on any branch push and publishes a full release when a v* tag is created.

Conventional commits
- Use conventional commits so semantic-release can determine the next version.
  - feat: -> minor
  - fix:, perf:, revert:, refactor:, docs:, style:, test:, build:, ci:, chore:, deps: -> patch
  - BREAKING CHANGE: in body or ! after type -> major
- To intentionally skip a release, use the scope "no-release", e.g., `chore(no-release): update dev tooling`.

How releases flow (no PRs required)
1) Merge PRs to main/master using conventional commit messages.
2) semantic-release computes the next version, pushes a vX.Y.Z tag, and creates a GitHub Release.
3) The tag triggers CI (`sbt ci-release`) to publish to Maven Central.
4) The push to main/master also publishes a SNAPSHOT for the same commit (expected and harmless).

Required secrets (for publishing)
- SONATYPE_USERNAME, SONATYPE_PASSWORD, PGP_SECRET (base64 ASCII-armored), PGP_PASSPHRASE

## Development
- Build: `sbt compile`
- Test: `sbt test`
- Publish locally: `sbt publishLocal`

Project structure
- Scala version: 3.3.1
- Organization: `us.awfl`
- Module name: `dsl`

## Contributing
Contributions are welcome! If you find a bug or have an idea:
- Open an issue describing the problem or proposal
- Or submit a pull request with a concise description and tests if relevant

Before contributing, please:
- Run the test suite locally (`sbt test`)
- Keep public APIs small and well-documented with scaladoc and examples

## License
MIT — see [LICENSE](LICENSE).
