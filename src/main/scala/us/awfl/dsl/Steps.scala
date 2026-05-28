package us.awfl.dsl

import io.circe.syntax._ // for .asJson
import io.circe.Encoder
import io.circe.generic.auto._
import scala.deriving.Mirror
import io.circe.Json
import CelOps._
import java.util.concurrent.atomic.AtomicLong
import scala.util.NotGiven
import scala.annotation.implicitNotFound

def init[T: Spec](name: String): Value[T] = {
  val resolver = Resolver(name)
  Value(resolver)
}

trait ValueInit[T, V <: BaseValue[T]] {
  def apply(name: String): V
  val nil: V
}
object ValueInit:
  given baseInit[T: Spec]: ValueInit[T, BaseValue[T]] = new ValueInit[T, BaseValue[T]] {
    def apply(name: String): BaseValue[T] = init(name)
    val nil = init("null")
  }

  given resolvedInit[T: Spec]: ValueInit[T, Resolved[T]] = new ValueInit[T, Resolved[T]] {
    def apply(name: String): Value[T] = init(name)
    val nil = init("null")
  }

  given valueInit[T: Spec]: ValueInit[T, Value[T]] = new ValueInit[T, Value[T]] {
    def apply(name: String): Value[T] = init(name)
    val nil = init("null")
  }

  given listInit[T: Spec]: ValueInit[T, ListValue[T]] = new ValueInit[T, ListValue[T]] {
    def apply(name: String): ListValue[T] = ListValue(Resolver(name))
    val nil = ListValue(Resolver("null"))
  }

sealed trait Step[T, +V <: BaseValue[T]](using vi: ValueInit[T, V]) {
  val name: String
  def resultValue: V = vi(s"${name}Result_${(hashCode & 0x7FFFFFFF).toHexString}")

  def fn = List(this) -> resultValue
}

extension[T](self: Step[T, Value[T]])
  def flatMapList[U: Spec](f: T => ListValue[U])(using ValueInit[U, ListValue[U]]): FlatMap[T, U, ListValue[U]] =
    FlatMap[T, U, ListValue[U]](self.name, self, self.resultValue.flatMap(f))

  def flatMap[U: Spec](
    f: T => Value[U]
  )(using ValueInit[U, Value[U]]): FlatMap[T, U, Value[U]] =
    FlatMap[T, U, Value[U]](self.name, self, self.resultValue.flatMap(f))

  def result: T = self.resultValue.get

case class Call[In, Out: Spec](name: String, call: String, args: BaseValue[In]) extends Step[Out, Value[Out]]

case class Return[T](name: String, value: BaseValue[T]) extends Step[NoValueT, NoValue]

case class For[In: Spec, Out: Spec](name: String, in: ListValue[In], item: Value[In], each: (List[Step[_, _]], BaseValue[Out])) extends Step[Out, ListValue[Out]]
object For {
  def apply[In: Spec, Out: Spec](name: String, in: ListValue[In])(each: Value[In] => (List[Step[_, _]], BaseValue[Out])): For[In, Out] = {
    val item: Value[In] = init(s"${name}Value")
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

case class Fold[B: Spec, T: Spec, W <: Resolved[B]](name: String, b: ValueType[B, W], list: ListValue[T], bResult: W, item: Value[T], run: (List[Step[_, _]], BaseValue[B]))(using vi: ValueInit[B, W]) extends Step[B, W] {
  override def resultValue: W = bResult
}
object Fold {
  def apply[B: Spec, T: Spec](name: String, b: BaseValue[B], list: ListValue[T])(run: (Value[B], Value[T]) => (List[Step[_, _]], BaseValue[B]))(using bInit: ValueInit[B, Value[B]]): Fold[B, T, Value[B]] = {
    val item: Value[T] = init(s"${name}Value")
    val dummyRun = run(bInit(s"${name}Dummy"), item)
    val hash = (List(name, b, list, item, dummyRun).hashCode  & 0x7FFFFFFF).toHexString
    val resultName = s"${name}Result_${hash}"
    val bResult = bInit(resultName)
    Fold(name, b, list, bResult, item, run(bInit(resultName), item))
  }
}

case class Switch[T: Spec, V <: Resolved[T]] private (name: String, cases: List[(Cel, (List[Step[_, _]], ValueType[T, V]))])(using ng: NotGiven[V =:= Resolved[T]], valueInit: ValueInit[T, V]) extends Step[T, V]
object Switch {
  @implicitNotFound("Switch.apply cannot be used when cases produce a ListValue[${T}]. Use Switch.list instead.")
  trait NotListValue[T, V]
  object NotListValue:
    given mk[T, V](using NotGiven[V <:< ListValue[T]]): NotListValue[T, V] = new {}

  def apply[T: Spec, V <: BaseValue[T]](name: String, cases: List[(Cel, (List[Step[_, _]], V))])(using valueInit: ValueInit[T, Value[T]], ng: NotListValue[T, V]): Switch[T, Value[T]] =
    Switch[T, Value[T]](name, cases)

  def list[T: Spec](name: String, cases: List[(Cel, (List[Step[_, _]], ListValue[T]))])(using ValueInit[T, ListValue[T]]): Switch[T, ListValue[T]] =
    Switch[T, ListValue[T]](name, cases)
}

type ValueType[T, V <: Resolved[T]] <: BaseValue[T] = V match {
  case Value[T] => BaseValue[T]
  case ListValue[T] => ListValue[T]
}

case class Try[T: Spec, V <: Resolved[T]](name: String, run: (List[Step[_, _]], ValueType[T, V]), error: Resolved[Error], except: (List[Step[_, _]], ValueType[T, V]))(using valueInit: ValueInit[T, V]) extends Step[T, V]
object Try {
  def apply[T: Spec](
    name: String,
    run: (List[Step[_, _]], BaseValue[T]),
    except: Value[Error] => (List[Step[_, _]], BaseValue[T])
  )(using valueInit: ValueInit[T, Value[T]]): Try[T, Value[T]] = {
    val error = init[Error](s"${name}Error")
    Try(name, run, error, except(error))
  }

  private def handle(err: Value[Error], reRaise: Boolean): List[Step[?,?]] = {
    List(Log(s"logError", err.flatMap(_.message))) ++ (
      if (reRaise) then List(Raise("reRaise", err))
      else List()
    )
  }

  def apply[T: Spec, V <: BaseValue[T]](
    name: String,
    run: (List[Step[_, _]], V),
    reRaise: Boolean = true
  )(using ng: NotGiven[V <:< ListValue[T]], valueInit: ValueInit[T, Value[T]]): Try[T, Value[T]] =
    Try(name, run, err => handle(err, reRaise) -> init("null"))

  def apply[T: Spec](
    name: String,
    run: (List[Step[_, _]], ListValue[T])
  )(using valueInit: ValueInit[T, ListValue[T]]): Try[T, ListValue[T]] = {
    val error = init[Error](s"${name}Error")
    Try(name, run, error, handle(error, true) -> ListValue("null"))
  }
}
case class Error(message: Value[String], code: Value[Int])
implicit val errorSpec: Spec[Error] = Spec { resolver =>
  import resolver._

  Error(in("message"), in("code"))
}

case class Raise(name: String, raise: BaseValue[Error]) extends Step[NoValueT, NoValue]

type Log = Call[Map[String, Value[String]], Nothing]
object Log {
  def apply(name: String, text: Resolved[String]): Log = {
    Call[Map[String, Value[String]], Nothing](name, "sys.log", obj(Map("text" -> str((s"[${name}] ": Cel) + text))))
  }
}

case class Block[T, V <: Resolved[T]](name: String, run: (List[Step[_, _]], V))(using valueInit: ValueInit[T, V]) extends Step[T, V] {
  val (steps, output) = run
  override def resultValue: V = output
}

case class FlatMap[T, U, V <: BaseValue[U]](name: String, step: Step[T, _], resultVal: V)(using valueInit: ValueInit[U, V]) extends Step[U, V] {
  override def resultValue: V = resultVal
}

case class Parallel(name: String, branches: List[Step[?, ?]]) extends Step[NoValueT, NoValue]

case class ParallelFor[In: Spec, Out: Spec](name: String, in: ListValue[In], item: Value[In], each: (List[Step[_, _]], BaseValue[Out])) extends Step[Out, ListValue[Out]]
object ParallelFor {
  def apply[In: Spec, Out: Spec](name: String, in: ListValue[In])(each: Value[In] => (List[Step[_, _]], BaseValue[Out])): ParallelFor[In, Out] = {
    val item: Value[In] = init(s"${name}Value")
    ParallelFor(name, in, item, each(item))
  }
}
