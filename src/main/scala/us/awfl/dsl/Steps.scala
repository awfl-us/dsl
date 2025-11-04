package us.awfl.dsl

import io.circe.syntax._ // for .asJson
import io.circe.Encoder
import io.circe.generic.auto._
import scala.deriving.Mirror
import io.circe.Json
import CelOps._
import java.util.concurrent.atomic.AtomicLong

def init[T: Spec](name: String): Value[T] = {
  val resolver = Resolver(name)
  Value(resolver)
}

trait ValueInit[T, V <: BaseValue[T]] {
  def apply(name: String): V
}
object ValueInit:
  given baseInit[T: Spec]: ValueInit[T, BaseValue[T]] = new ValueInit[T, BaseValue[T]] {
    def apply(name: String): BaseValue[T] = init(name)
  }

  given resolvedInit[T: Spec]: ValueInit[T, Resolved[T]] = new ValueInit[T, Resolved[T]] {
    def apply(name: String): Resolved[T] = init(name)
  }

  given valueInit[T: Spec]: ValueInit[T, Value[T]] = new ValueInit[T, Value[T]] {
    def apply(name: String): Value[T] = init(name)
  }

  given listInit[T: Spec]: ValueInit[T, ListValue[T]] = new ValueInit[T, ListValue[T]] {
    def apply(name: String): ListValue[T] = ListValue(Resolver(name))
  }

object HexCounter {
  private val counter = new AtomicLong(0L)

  /** Returns the next counter value as a lowercase hexadecimal string. */
  def next(): String = counter.getAndIncrement().toHexString
}

sealed trait Step[T, +V <: BaseValue[T]](using vi: ValueInit[T, V]) {
  val name: String
  def resultValue: V = vi(s"${name}Result_${(hashCode & 0x7FFFFFFF).toHexString}")
  def result: T = resultValue.get

  def fn = List(this) -> resultValue
}

sealed trait ValueStep[T] extends Step[T, BaseValue[T]] { self: Step[T, BaseValue[T]] =>
  def flatMap[U: Spec, V <: BaseValue[U]](f: T => V)(using valueInit: ValueInit[U, V]): FlatMap[T, U, V] = FlatMap[T, U, V](name, this, self.resultValue match {
    case resolved: Resolved[T] => resolved.flatMap(f)
    case obj: Obj[T] => f(obj.value)
  })
}

case class Call[In, Out: Spec](name: String, call: String, args: BaseValue[In]) extends Step[Out, Value[Out]] with ValueStep[Out]

case class Return[T](name: String, value: BaseValue[T]) extends Step[NoValueT, NoValue]

case class For[In: Spec, Out: Spec](name: String, in: ListValue[In], item: Resolved[In], each: (List[Step[_, _]], BaseValue[Out])) extends Step[Out, ListValue[Out]]
object For {
  def apply[In: Spec, Out: Spec](name: String, in: ListValue[In])(each: Resolved[In] => (List[Step[_, _]], BaseValue[Out])): For[In, Out] = {
    val item: Resolved[In] = init(s"${name}Value")
    For(name, in, item, each(item))
  }
}

case class ForRange[Out: Spec](name: String, from: Cel, to: Cel, idx: CelConst, each: (List[Step[_, _]], BaseValue[Out])) extends Step[Out, ListValue[Out]]
object ForRange {
  def apply[Out: Spec](name: String, from: Cel, to: Cel)(each: Cel => (List[Step[_, _]], BaseValue[Out])): ForRange[Out] = {
    val idx: CelConst = CelConst(s"${name}Idx")
    ForRange(name, from, to, idx, each(idx))
  }
}

case class Fold[B: Spec, T: Spec](name: String, b: BaseValue[B], list: ListValue[T], bResult: BaseValue[B], item: Value[T], run: (List[Step[_, _]], BaseValue[B])) extends Step[B, BaseValue[B]] {
  override def resultValue: BaseValue[B] = bResult
}
object Fold {
  def apply[B: Spec, T: Spec](name: String, b: BaseValue[B], list: ListValue[T])(run: (BaseValue[B], Value[T]) => (List[Step[_, _]], BaseValue[B])): Fold[B, T] = {
    val item: Value[T] = init(s"${name}Value")
    val dummyRun = run(Value[B](b.cel + "dummy"), item)
    val hash = (List(name, b, list, item, dummyRun).hashCode  & 0x7FFFFFFF).toHexString
    val bResult = Value[B](s"${name}Result_${hash}")
    Fold(name, b, list, bResult, item, run(bResult, item))
  }
}

case class Switch[T: Spec, V <: BaseValue[T]](name: String, cases: List[(Cel, (List[Step[_, _]], V))])(using valueInit: ValueInit[T, V]) extends Step[T, V] with ValueStep[T]

case class Try[T: Spec, V <: BaseValue[T]](name: String, run: (List[Step[_, _]], V), except: Resolved[Error] => (List[Step[_, _]], V))(using valueInit: ValueInit[T, V]) extends Step[T, V] with ValueStep[T]
object Try {
  def apply[T: Spec, V <: BaseValue[T]](
    name: String,
    run: (List[Step[_, _]], V)
  )(using valueInit: ValueInit[T, V]): Try[T, V] =
    new Try(name, run, err => List(Log(s"${name}_logError", err.flatMap(_.message))) -> valueInit("null"))
}

case class Error(message: BaseValue[String], code: BaseValue[String])
implicit val errorSpec: Spec[Error] = Spec { resolver =>
  import resolver._

  Error(in("message"), in("code"))
}

case class Raise(name: String, raise: BaseValue[Error]) extends Step[NoValueT, NoValue]

type Log = Call[Map[String, Value[String]], Nothing]
object Log {
  def apply(name: String, text: BaseValue[String]): Log = {
    Call[Map[String, Value[String]], Nothing](name, "sys.log", obj(Map("text" -> str((s"[${name}] ": Cel) + text))))
  }
}

case class Block[T, V <: BaseValue[T]](name: String, run: (List[Step[_, _]], V))(using valueInit: ValueInit[T, V]) extends Step[T, V] with ValueStep[T] {
  val (steps, output) = run
  override def resultValue: V = {
    // throw new RuntimeException(s"Block result: ${output.cel}/${super.resultValue.cel}")
    output
  }
}

case class FlatMap[T, U, V <: BaseValue[U]](name: String, step: Step[T, BaseValue[T]], resultVal: V)(using valueInit: ValueInit[U, V]) extends Step[U, V] with ValueStep[U] {
  override def resultValue: V = resultVal
}
