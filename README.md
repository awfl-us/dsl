dsl — Workflow DSL for Scala 3

A compact, type-safe DSL to model workflows as pure data. It gives you three core building blocks:
- Values: typed, addressable data (scalars, objects, lists) backed by Resolvers
- CEL expressions: composable expressions over values (comparisons, math, string ops, function calls)
- Steps: declarative control-flow nodes that produce Values you can wire together

This README is a practical guide to using Values, CEL, and Steps.

Install (SBT)
- Organization: us.awfl
- Module: dsl
- Scala: 3.3.1
- Version: 0.1.0-SNAPSHOT (use the latest release when available)

libraryDependencies += "us.awfl" %% "dsl" % "0.1.0-SNAPSHOT"

If building locally first:
- In this repo: sbt publishLocal
- In your project: resolvers += Resolver.mavenLocal

Concepts at a glance
- Value[T]: a typed handle pointing at data (input, intermediate, or computed)
- ListValue[T]: a Value representing a list of T, indexable with a CEL index
- Resolver: a path inside the workflow context used to bind/resolve Values
- CEL: a small expression language used in conditions, math, selections, and construction of derived values
- Step[T, V <: BaseValue[T]]: a unit of work that produces a V (commonly Value[T] or ListValue[T])

Import helpers
import us.awfl.dsl._
import us.awfl.dsl.CelOps._          // operators and conversions to build CEL
import us.awfl.dsl.auto.given        // derive Spec for case classes

Values: addressing and composing data
- init[T: Spec](name): create an anchored Value[T] at a named root (e.g., "input")
  val input = init[User]("input")

- Base types
  - BaseValue[T]: common supertype with .get (resolved via Spec) and .cel (CEL view)
  - Value[T]: a resolved value at a Resolver path
  - ListValue[T]: a resolved list; index via list(iCel) to get Value[T]
  - Obj[T]: a literal inlined object/value; build with obj(value)
  - Field: a string field reference at the current Resolver

- Resolver basics
  - Build a Value nested under a field: resolver.in[T]("field")
  - Build a ListValue nested under a field: resolver.list[T]("items")
  - Create a root resolver for a name: Resolver("input")

- Accessing nested fields via case classes
  Define models with Value[...] fields and derive a Spec:
  case class User(name: Value[String], id: Value[String])
  val user: Value[User] = init[User]("input")
  val name: Value[String] = user.flatMap(_.name)

- Literal and derived values
  - obj(42): BaseValue[Int] as a literal
  - Value[T](cel: Cel): wrap a CEL expression as a Value[T]
  - str(cel: Cel): Value[String] from a CEL expression

- Lists and utilities
  - list(i): Index into a ListValue with a CEL index to get Value[T]
  - len(list): Cel — length of a list
  - buildList(name, List(a,b,c)): Step that yields ListValue[T] from Scala constants
  - buildValueList(name, List(v1,v2)): like buildList but from BaseValue[T]
  - join(name, lists*): Step that concatenates multiple lists into one ListValue[T]
  - joinSteps(steps*): Step that joins the resultValue of multiple list-producing steps

- Optional values/lists with defaults
  - OptValue[T](resolver).getOrElse(default: BaseValue[T]) -> BaseValue[T]
  - OptList[T](resolver).getOrElse(default: ListValue[T]) -> ListValue[T]
  Examples:
  val maybeName = OptValue[String](user.resolver.in[String]("nickname")).getOrElse(name)
  val safeList  = OptList[String](Resolver("input").list[String]("tags")).getOrElse(ListValue.empty[String])

CEL: expressions you can compose
- You write CEL by combining Values, literals, and operators via CelOps. A BaseValue[T] exposes .cel, and implicit conversions let you write natural expressions.

- Common operators (non-exhaustive)
  - Equality/relational: a === b, a !== b, a > b, a >= b, a < b, a <= b
  - Boolean: cond1 && cond2, cond1 || cond2
  - Arithmetic: a + b, a - b, a * b, a / b, a % b
  - String build: ("Hello, ": Cel) + name + "!"  // coerce strings to Cel then concatenate

- Useful CEL functions available via helpers in this module
  - len(list): Cel
  - encodeJson(value): Cel, uses json.encode_to_string
  - default(value, fallback) is used internally by OptValue/OptList
  - map.get and list indexing are represented in the CEL AST (CelFunc, CelAt, CelPath)

- Turn a CEL expression into a Value
  val greeting: Value[String] = str(("Hello, ": Cel) + name)

Steps: building workflows
- Anatomy
  trait Step[T, +V <: BaseValue[T]] {
    val name: String
    def resultValue: V   // feed this into other steps
    def result: T        // Materialize via Spec[T] (for interpretation/serialization)
  }

- Compose steps by wiring their resultValue into later steps or by using .flatMap on steps that extend ValueStep.

Step catalog and usage
- Call[In, Out]
  - Purpose: represent an external call/invocation.
  - Signature: Call(name: String, call: String, args: BaseValue[In]) extends Step[Out, Value[Out]] with ValueStep[Out]
  - Example:
    case class Params(id: Value[String])
    case class Response(body: Value[String])
    val req = obj(Params(id = user.flatMap(_.id)))
    val fetch = Call[Params, Response]("fetchUser", "service.user.fetch", req)
    val body: Value[String] = fetch.flatMap(_.body)

- Log (convenience over Call)
  - Purpose: write a log line.
  - Usage: val log = Log("greet", str(("Hello ": Cel) + name))

- Return[T]
  - Purpose: model returning from the current scope/pipeline.
  - Usage: Return("done", obj(Map("status" -> obj("ok"))))

- Raise
  - Purpose: raise an Error with message and code.
  - Example:
    val err = obj(Error(message = str("missing input"), code = str("BadRequest")))
    val raise = Raise("fail", err)

- For[In, Out]
  - Purpose: map over a ListValue[In] to produce a ListValue[Out].
  - Builder: For(name, inList) { item => (steps, elementValue) }
  - Example:
    val items: ListValue[String] = Resolver("input").list[String]("names")
    val upper: Step[String, ListValue[String]] = For("toUpper", items) { item =>
      val up = str(item.cel.call("toUpperCase")) // or your own CEL function
      List(Log("log_each", up)) -> up
    }
    val upperList: ListValue[String] = upper.resultValue

- ForRange[Out]
  - Purpose: index-based loop from from (inclusive) to to (exclusive) producing a ListValue[Out].
  - Builder: ForRange(name, from: Cel, to: Cel) { idx => (steps, elementValue) }
  - Example:
    val greetings = ForRange[String]("greetings", from = 0, to = 3) { i =>
      val msg = str(("Hello #": Cel) + i)
      List(Log("log_msg", msg)) -> msg
    }.resultValue

- Fold[B, T]
  - Purpose: reduce a list into a single accumulator of type B.
  - Builder: Fold(name, initial: BaseValue[B], list: ListValue[T]) { (acc, item) => (steps, nextAcc) }
  - Example (sum ints):
    val ints: ListValue[Int] = Resolver("input").list[Int]("nums")
    val sumStep = Fold[Int, Int]("sum", obj(0), ints) { (acc, i) =>
      List() -> Value[Int](acc.cel + i)
    }
    val sum: BaseValue[Int] = sumStep.resultValue

- Switch[T, V <: BaseValue[T]]
  - Purpose: choose the first matching case based on CEL conditions, producing V.
  - Example:
    val choose = Switch[String, Value[String]](
      name = "choose",
      cases = List(
        (name.cel === "alice") -> (List() -> str("hi alice")),
        (name.cel === "bob")   -> (List() -> str("hi bob")),
        (true: Cel)             -> (List() -> str("hi there"))
      )
    )

- Try[T, V <: BaseValue[T]]
  - Purpose: run a block and handle failures; except callback receives a typed Error value.
  - Builder: Try(name, run = (steps, resultValue)) with optional except: err => (steps, fallback)
  - Example:
    val risky = Call[Params, Response]("risky", "service.risky", req)
    val safe  = Try[Response, Value[Response]](
      name = "safe",
      run = List(risky) -> risky.resultValue
    )

- Block[T, V]
  - Purpose: group steps and explicitly surface an output value.
  - Example:
    val block = Block("group", List(Log("start", str("ok"))), greetings)

- FlatMap
  - Purpose: produced by ValueStep.flatMap to transform a step result into another value/step while preserving chaining semantics.
  - Example: val body: Value[String] = fetch.flatMap(_.body)

Composing pipelines
- Each Step has a resultValue you can feed into later steps. Group multiple steps with Block when you need to return a specific value while keeping internal steps ordered.
- When a Step mixes control flow and values (For, ForRange, Fold, Switch, Try), its resultValue is the thing you’ll thread forward (e.g., the resulting list or computed value).

End-to-end mini example
case class User(name: Value[String])
val user = init[User]("input")
val name = user.flatMap(_.name)

// Log once and build three greetings
val greetings: ListValue[String] = ForRange[String]("greetings", 0, 3) { i =>
  val msg = str(("Hello, ": Cel) + name + " #" + i)
  List(Log("log_msg", msg)) -> msg
}.resultValue

val program = Block("example", List(Log("log_user", name)) -> greetings)

Notes on interpretation/serialization
- This DSL is descriptive. You can interpret Steps/Values/CEL however you like (e.g., compile to YAML/JSON, send to an engine, or evaluate in a custom runtime).
- Specs (Spec[T]) define how to resolve/marshal types from Resolver paths; import us.awfl.dsl.auto.given for case-class derivation.

Build
- sbt compile
- sbt test
- sbt publishLocal

License
MIT (see LICENSE).