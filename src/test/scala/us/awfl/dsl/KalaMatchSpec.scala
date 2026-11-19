package us.awfl.dsl

import munit.FunSuite
import us.awfl.dsl.auto.given

// Domain model for Kala matching
sealed trait KalaVibhaga extends IsAdt
case class SegKala(sessionId: Value[String], end: Value[Int], windowSeconds: Value[Int]) extends KalaVibhaga
case class SessionKala(sessionId: Value[String], end: Value[Int]) extends KalaVibhaga
case class WeekKala(end: Value[Int]) extends KalaVibhaga
case class TermKala(termBegin: Value[Int], end: Value[Int]) extends KalaVibhaga

object Ctx {
  case class Kala(
    kind: String,
    sessionId: OptValue[String],
    end: OptValue[Int],
    windowSeconds: OptValue[Int],
    termBegin: OptValue[Int]
  )

  def segKala(sessionId: Value[String], end: Value[Int], windowSeconds: Value[Int]): Kala =
    Kala("seg", OptValue(sessionId), OptValue(end), OptValue(windowSeconds), OptValue.nil)

  def sessionKala(sessionId: Value[String], end: Value[Int]): Kala =
    Kala("session", OptValue(sessionId), OptValue(end), OptValue.nil, OptValue.nil)

  def weekKala(end: Value[Int]): Kala =
    Kala("week", OptValue.nil, OptValue(end), OptValue.nil, OptValue.nil)

  def termKala(termBegin: Value[Int], end: Value[Int]): Kala =
    Kala("term", OptValue.nil, OptValue(end), OptValue.nil, OptValue(termBegin))
}

// The encode function using Match and typed binders on RHS
def encode(k: Value[KalaVibhaga]): Step[Ctx.Kala, Value[Ctx.Kala]] =
  Match("encodeKalaVal", k) {
    case seg: SegKala      => List() -> obj(Ctx.segKala(seg.sessionId, seg.end, seg.windowSeconds))
    case sesh: SessionKala => List() -> obj(Ctx.sessionKala(sesh.sessionId, sesh.end))
    case week: WeekKala    => List() -> obj(Ctx.weekKala(week.end))
    case term: TermKala    => List() -> obj(Ctx.termKala(term.termBegin, term.end))
  }

final class KalaMatchSpec extends FunSuite {
  test("encode KalaVibhaga with typed binders compiles and builds Step") {
    val v: Value[KalaVibhaga] = init[KalaVibhaga]("kala")
    val step: Step[Ctx.Kala, Value[Ctx.Kala]] = encode(v)
    // Match builds a Block named s"${name}_block"
    assertEquals(step.name, "encodeKalaVal_block")
  }

  test("pattern field binders are accessible and Step is named matchId_block") {
    val k: Value[KalaVibhaga] = init[KalaVibhaga]("kala")
    val matchId: Step[String, Value[String]] =
      Match("matchId", k) {
        case SegKala(sessionId, _, _)   => List() -> sessionId
        case SessionKala(sessionId, _)  => List() -> sessionId
        // For non-session contexts, fall back to a stable key to avoid NPEs; ideally
        // callers of sessionKey should provide a session-bearing KalaVibhaga.
        case WeekKala(_)                => List() -> str("global")
        case TermKala(_, _)             => List() -> str("global")
      }

    assertEquals(matchId.name, "matchId_block")
  }
}
