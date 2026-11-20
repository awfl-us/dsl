package us.awfl.dsl

import us.awfl.dsl.auto.given
import us.awfl.dsl.CelOps.*

/** Literal field matching in the original candidate-JSON algorithm. */
object LiteralPatternSmoke:
  sealed trait Event extends IsAdt
  case class Created(role: String, id: Value[String]) extends Event
  case class Deleted(role: String, id: Value[String]) extends Event

  def route(in: Value[Event]): Step[String, Value[String]] =
    Match("roleRoute", in) {
      case Created("admin", id) => List() -> str(id.cel + CelStr("@admin"))
      case Created("user", id) => List() -> str("user")
      case Deleted("admin", id) => List() -> str("deleted")
      case _ => List() -> str("other")
    }

  def main(args: Array[String]): Unit =
    val step = route(Value[Event]("input")).asInstanceOf[Block[String, Value[String]]]
    val cases = step.steps(1).asInstanceOf[Switch[String, Value[String]]].cases
    assert(cases.size == 5) // 3 literal cases, wildcard, unmatched error
    assert(cases.head._1.toString.contains("json.encode_to_string"))
    assert(cases(3)._1.toString.contains("json.encode_to_string"))
    println("DSL_ADT_LITERAL_PATTERN_PASS")
