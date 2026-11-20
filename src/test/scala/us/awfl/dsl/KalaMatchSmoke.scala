package us.awfl.dsl

import us.awfl.dsl.auto.given

/** Real use-site shapes copied from workflow-utils/wip-old. */
object KalaMatchSmoke:
  sealed trait Kala extends IsAdt
  case class SegKala(sessionId: Value[String], end: Value[Double], windowSeconds: Value[Double]) extends Kala
  case class SessionKala(sessionId: Value[String], sessionEnd: Value[Double]) extends Kala
  case class WeekKala(weekEnd: Value[Double]) extends Kala
  case class TermKala(begin: Value[Double], end: Value[Double]) extends Kala
  case class Encoded(kind: Value[String], sessionId: OptValue[String])

  def lockKey(value: Value[Kala]): Step[String, Value[String]] =
    Match("matchId", value) {
      case SegKala(sessionId, _, _) => List() -> sessionId
      case SessionKala(sessionId, _) => List() -> sessionId
      case WeekKala(_) => List() -> str("global")
      case TermKala(_, _) => List() -> str("global")
    }

  def encode(value: Value[Kala]): Step[Encoded, Value[Encoded]] =
    Match("encodeKalaVal", value) {
      case seg: SegKala => List() -> obj(Encoded(str("SegKala"), OptValue(seg.sessionId)))
      case session: SessionKala => List() -> obj(Encoded(str("SessionKala"), OptValue(session.sessionId)))
      case week: WeekKala => List() -> obj(Encoded(str("WeekKala"), OptValue.nil))
      case term: TermKala => List() -> obj(Encoded(str("TermKala"), OptValue.nil))
    }

  def typeOnly(value: Value[Kala]): Step[String, Value[String]] =
    Match("typeOnly", value) {
      case _: SegKala => List() -> str("segment")
      case _ => List() -> str("other")
    }

  def main(args: Array[String]): Unit =
    val src = Value[Kala]("input")
    val step = lockKey(src).asInstanceOf[Block[String, Value[String]]]
    assert(step.name == "matchId_block")
    val candidates = step.steps.head.asInstanceOf[ForRange[Kala]]
    val selection = step.steps(1).asInstanceOf[Switch[String, Value[String]]]
    assert(candidates.name == "matchId_caseValues")
    assert(selection.cases.size == 5)
    assert(selection.cases.take(4).forall(_._1.toString.contains("json.encode_to_string")))
    assert(selection.cases.last._2._1.head.isInstanceOf[Raise])
    assert(candidates.each._1.head.isInstanceOf[Switch[?, ?]])
    val id = selection.cases.head._2._2.asInstanceOf[Value[String]]
    assert(id.resolver.path.path.toString.contains("sessionId"))
    val contextStep = encode(src).asInstanceOf[Block[Encoded, Value[Encoded]]]
    val ctxCandidates = contextStep.steps.head.asInstanceOf[ForRange[Kala]]
    val ctxSwitch = contextStep.steps(1).asInstanceOf[Switch[Encoded, Value[Encoded]]]
    assert(ctxCandidates.name == "encodeKalaVal_caseValues")
    assert(ctxSwitch.cases.size == 5)
    val typedOnly = typeOnly(src).asInstanceOf[Block[String, Value[String]]]
    val typeCases = typedOnly.steps(1).asInstanceOf[Switch[String, Value[String]]].cases
    assert(typeCases.size == 3)
    assert(typeCases.head._1.toString.contains("json.encode_to_string"))
    println("DSL_ADT_MATCH_KALA_PASS")
