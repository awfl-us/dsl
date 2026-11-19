Macros: extracting matched case values for the AWFL DSL

Overview
- Inline API exposes matchedCases that inspects a literal PartialFunction at compile time, reconstructs the matched value for each supported branch, and returns a List[(A, B)] with one entry per branch that matches the provided value (order preserved).
- This aligns with downstream usage that expects raw A values (not wrapped in obj/BaseValue).

Public API
inline def matchedCases[A, B](inline value: A)(inline pf: PartialFunction[A, B]): List[(A, B)]

Behavior
- For each supported case pattern, generates a small match expression against `value`:
  - If the branch pattern matches, reconstructs the matched A value (see Supported patterns) and produces Some(matched -> ORIGINAL_BODY)
  - Otherwise produces None
- Collects all per-branch Options into a List and flattens, yielding List[(A, B)] in the same order as cases are written by the user.
- If no branches match, returns Nil.

Supported patterns (v1)
- case SingletonObject =>
- case CaseClass(binders...) =>  // only simple binders inside constructor args
- case Literal =>
- case _ =>

Rejected in v1 (fails with compile-time error)
- Guards (if ...)
- Or-patterns (p1 | p2)
- Nested destructuring in constructor args (e.g., CaseClass(Nested(...)))
- Typed patterns (x: T or _: T)
- Extractors that are not case-class companions

Notes
- The PartialFunction must be provided inline as a literal { case ... } block. Passing a pre-defined val/def will fail with a helpful error.
- The macro does not change user variable names in patterns; binders remain in scope for the branch body.

Examples
sealed trait Status
case object Pending extends Status
case object Done extends Status
case class Failed(code: Int, reason: String) extends Status

matchedCases(Pending) {
  case Pending => "pending"
  case Done    => "done"
}
// => List(Pending -> "pending")

matchedCases(Failed(500, "boom")) {
  case Failed(code, reason) => s"$code:$reason"
}
// => List(Failed(500, "boom") -> "500:boom")

// Multiple matches when a wildcard is present (order preserved):
matchedCases(Pending) {
  case Pending => "p"
  case _       => "any"
}
// => List(
//      Pending -> "p",
//      Pending -> "any"
//    )

Integration hints
- matchedCases enumerates all matching branches and returns a List of pairs. If you need to perform equivalence/dedup later in your workflow, do it downstream after receiving this List.
- The result intentionally uses raw A to integrate with components that expect unwrapped values (e.g., buildList in Steps.scala).
