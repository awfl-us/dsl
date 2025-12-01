package us.awfl.dsl

import io.circe.Encoder
import scala.annotation.tailrec
import us.awfl.dsl.CelOps._

sealed trait BaseValue[T] {
  def get: T
  def flatMap[U, V <: BaseValue[U]](f: T => V): V = f(get)
  val cel = CelValue(this)
}

case class Resolver(path: CelPath = CelPath(List())) {
  def field(name: String): Field = FieldValue(Resolver(path :+ CelConst(name)))
  def in[T: Spec](name: String): Value[T] = Value(Resolver(path :+ CelConst(name)))
  def list[T: Spec](name: String): ListValue[T] = ListValue(Resolver(path :+ CelConst(name)))

  def :+(right: String): Resolver = Resolver(path :+ right)

  def ++(other: Resolver): Resolver = Resolver(this.path ++ other.path)
}
object Resolver {
  def apply(name: String): Resolver = Resolver(CelPath(CelConst(name) :: Nil))
}

def str(cel: Cel): Value[String] = Value(Resolver(CelPath(cel :: Nil)))

def encodeJson(value: BaseValue[_]): Cel = CelFunc("json.encode_to_string", value.cel)

sealed trait Resolved[T] extends BaseValue[T] {
  val resolver: Resolver

  def ++:(left: Resolved[_]): Resolved[T]

  def field: Field = FieldValue(resolver)
}

type Field = FieldValue
case class FieldValue(resolver: Resolver) extends Resolved[String] {
  override def get: String = sys.error("You can't get Nothing!")
  override def ++:(left: Resolved[_]): Resolved[String] = FieldValue(left.resolver ++ resolver)
}
object Field {
  def apply(cel: String): Field = FieldValue(Resolver(cel))
  def apply(cel: Cel): Field = FieldValue(Resolver(CelPath(cel :: Nil)))
  def str(cel: Cel): Value[String] = Value(Resolver(CelPath(cel :: Nil)))
}

case class Value[T: Spec](resolver: Resolver) extends Resolved[T] {
  def copy(resolver1: Resolver): Value[T] = Value(resolver ++ resolver1)
  def get: T = implicitly[Spec[T]].init(resolver)
  override def ++:(left: Resolved[?]): Resolved[T] = Value(left.resolver ++ resolver)
}
object Value {
  def apply[T: Spec](value: String): Value[T] = Value(Resolver(value))
  def apply[T: Spec](cel: Cel): Value[T] = Value(Resolver(CelPath(List(cel))))
  def nil[T: Spec]: Value[T] = Value(CelConst("null"))
}

case class Obj[T](value: T) extends BaseValue[T] {
  override def get: T = value

  def base: BaseValue[T] = this
}
def obj[T](value: T): Obj[T] = Obj(value)

case class ListValue[T: Spec](resolver: Resolver) extends Resolved[T] { self =>
  private def at(i: Cel): Cel = CelAt(this, i)
  def apply(i: Cel): Value[T] = Value(Resolver(CelPath(at(i) :: Nil)))
  override def get: T = implicitly[Spec[T]].init(resolver)
  override def ++:(left: Resolved[?]): Resolved[T] = ListValue(left.resolver ++ resolver)
}

object ListValue {
  def apply[T: Spec](cel: Cel): ListValue[T] = ListValue(Resolver(CelPath(List(cel))))
  def empty[T: Spec]: ListValue[T] = ListValue(CelConst("[]"))
  def nil[T: Spec]: ListValue[T] = ListValue(CelConst("null"))
}

def buildList[T: Spec](name: String, list: List[T]): Step[T, ListValue[T]] = ForRange(name, 0, list.size) { i =>
  val switchStep = Switch[T, BaseValue[T]](s"${name}_switch", list.zipWithIndex.map { case (e, i2) =>
    (i === i2) -> (List() -> obj(e))
  })

  List(switchStep) -> switchStep.resultValue
}

def buildValueList[T: Spec](name: String, list: List[BaseValue[T]]): Step[T, ListValue[T]] = ForRange(name, 0, list.size) { i =>
  val switchStep = Switch(s"${name}_switch", list.zipWithIndex.map { case (e, i2) =>
    (i === i2) -> (List() -> e)
  })

  List(switchStep) -> switchStep.resultValue
}

def len(list: ListValue[_]): Cel = CelFunc("len", CelValue(list))

def join[T: Spec](name: String, lists: ListValue[T]*): Step[T, ListValue[T]] = {
  val withCumulative = lists.toList.zipWithIndex.map { (l, n) =>
    lists.toList.take(n).foldLeft(0: Cel) { (b, item) =>
      b + len(item)
    } -> l
  }

  ForRange(name, 0, withCumulative.last._1 + len(lists.last)) { i =>
    Switch(s"${name}_switch", withCumulative.reverse.map { (start, l) =>
      (i >= start) -> (List() -> l(i - start))
    }).fn
  }
}

def joinSteps[T: Spec](name: String, steps: Step[T, ListValue[T]]*): Step[T, ListValue[T]] = {
  val joinAll = join(s"${name}_joinSteps", steps.map(_.resultValue): _*)
  Block(s"${name}_joinSteps", (steps.toList :+ joinAll) -> joinAll.resultValue)
}

type NoValueT = Map[String, String]
implicit val noValSpec: Spec[NoValueT] = Spec(_ => sys.error("No value!"))
type NoValue = Value[NoValueT]
val noValue: Value[NoValueT] = Value.nil

type AnyValueT = Map[String, Int]
implicit val anyValueSpec: Spec[AnyValueT] = Spec(_ => Map())
type AnyValue = Value[AnyValueT]

type AnyObj = Obj[AnyValueT]

implicit val nothingSpec: Spec[Nothing] = Spec(_ => sys.error("No spec for Nothing!"))

sealed trait OptValue[T] {
  def getOrElse(default: BaseValue[T]): BaseValue[T]
}
case class OptResolved[T: Spec](resolver: Resolver) extends OptValue[T] {
  def getOrElse(default: BaseValue[T]): BaseValue[T] = resolver.path.path match {
    case single :: Nil => Value(CelFunc("default", single, default.cel))
    case multiple @ (_ :: _ :: _) => multiple.last match {
      case CelConst(last) =>
        Value(CelFunc("default", CelFunc("map.get", CelPath(multiple.init), last), default.cel))
      case _ =>
        Value(CelFunc("default", CelPath(multiple), default.cel))
    }
      
    case Nil => sys.error("Unexpected empty resolver")
  }

  val spec = summon[Spec[T]]
}
case class OptObj[T](obj: Obj[T]) extends OptValue[T] {
  def getOrElse(default: BaseValue[T]): BaseValue[T] = obj
}
object OptValue {
  def apply[T: Spec](cel: Cel): OptValue[T] = OptResolved[T](Resolver(CelPath(List(cel))))
  def apply[T: Spec](obj: Obj[T]): OptValue[T] = OptObj(obj)
  def nil[T: Spec]: OptValue[T] = OptValue(CelConst("null"))
}

case class OptList[T: Spec](resolver: Resolver) {
  def getOrElse(default: ListValue[T]): ListValue[T] = ListValue(OptResolved(resolver).getOrElse(default).cel)

  val spec = summon[Spec[T]]
}
object OptList {
  def apply[T: Spec](cel: Cel): OptList[T] = OptList(Resolver(CelPath(List(cel))))
  def nil[T: Spec]: OptList[T] = OptList(CelConst("null"))
}
