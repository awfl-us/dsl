package us.awfl.dsl

/** Compiler-facing CEL AST regression for the historical optional-value API. */
object OptionalValueSmoke:
  def main(args: Array[String]): Unit =
    val direct = OptValue[String](CelConst("null"))
    val directPresent = direct.present.cel
    assert(directPresent.toString.contains("default"))
    val defaulted = direct.getOrElse(str("fallback"))
    assert(defaulted.cel.toString.contains("default"))

    // Missing nested fields are read with map.get rather than unsafe CEL paths.
    val nested = OptResolved[String](Resolver("input").in[String]("optional").resolver)
    val nestedDefault = nested.getOrElse(str("fallback")).cel.toString
    assert(nestedDefault.contains("map.get"))
    assert(nestedDefault.contains("default"))
    val nestedMapped = nested.map(v => str(v.cel)).getOrElse(str("none")).cel.toString
    assert(nestedMapped.contains("map.get"))
    assert(nestedMapped.contains("default"))
    assert(nested.present.cel.toString.contains("map.get"))
    println("DSL_OPTIONAL_VALUE_PASS")
