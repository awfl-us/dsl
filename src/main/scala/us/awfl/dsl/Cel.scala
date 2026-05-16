package us.awfl.dsl

import scala.language.implicitConversions

sealed trait Cel

case class CelValue(value: BaseValue[_]) extends Cel

case class CelConst(value: String) extends Cel

case class CelStr(content: String) extends Cel {
  def safe: CelStr = CelStr(
    content.flatMap {
      case '"'  => "\\\""     // escape double quotes
      case '\\' => "\\\\"     // escape backslashes
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c if c.isControl =>
        "\\u%04x".format(c.toInt) // other control chars
      case c => c.toString
    }
  )
}

case class CelOp(left: Cel, op: String, right: Cel) extends Cel

case class CelFunc(name: String, params: List[Cel]) extends Cel
object CelFunc {
  def apply(name: String, params: Cel*): CelFunc = CelFunc(name, params.toList)
}

case class CelAt(list: ListValue[_], i: Cel) extends Cel

case class CelPath(path: List[Cel]) extends Cel {
  def ++(other: CelPath): CelPath = CelPath(this.path ++ other.path)
  def :+(elem: Cel): CelPath = CelPath(this.path :+ elem)
}

object Cel {
  val nil: Cel = CelConst("null")

  // Implicit: String => Cel (quoted)
  given Conversion[String, Cel] with
    def apply(s: String): Cel = CelStr(s)

  // Implicit: Int => Cel
  given Conversion[Int, Cel] with
    def apply(i: Int): Cel = CelConst(i.toString)

  given Conversion[Double, Cel] with
    def apply(i: Double): Cel = CelConst(i.toString)

  // Implicit: Boolean => Cel
  given Conversion[Boolean, Cel] with
    def apply(b: Boolean): Cel = CelConst(b.toString)

  given Conversion[Resolved[_], Cel] with
    def apply(v: Resolved[_]): Cel = CelValue(v)
}

object CelOps {
  extension (left: Cel)
    def ===(right: Cel): Cel = CelOp(left, "==", right)
    def !==(right: Cel): Cel = CelOp(left, "!=", right)
    def >(right: Cel): Cel = CelOp(left, ">", right)
    def >=(right: Cel): Cel = CelOp(left, ">=", right)
    def <(right: Cel): Cel = CelOp(left, "<", right)
    def <=(right: Cel): Cel = CelOp(left, "<=", right)
    def +(right: Cel): Cel = CelOp(left, "+", right)
    def -(right: Cel): Cel = CelOp(left, "-", right)
    def *(right: Cel): Cel = CelOp(left, "*", right)
    def `//`(right: Cel): Cel = CelOp(left, "//", right)
    def in(list: Cel): Cel = CelOp(left, "in", list)
    def &&(right: Cel): Cel = CelOp(left, "and", right)
    def ||(right: Cel): Cel = CelOp(left, "or", right)
    def unary_! : Cel = CelOp(CelConst("not"), "", left)

  // Extension to build a Cel list constant from a Scala List
  extension [A](xs: List[A])
    def cel: CelConst =
      val body = xs.map(renderListElem).mkString("[", ", ", "]")
      CelConst(body)

  // Renders individual list elements into CEL literal strings
  private def renderListElem(a: Any): String = a match
    case s: String => "\"" + CelStr(s).safe.content + "\""
    case c: Char   => "\"" + CelStr(c.toString).safe.content + "\""
    case b: Boolean => b.toString
    case i: Byte => i.toString
    case i: Short => i.toString
    case i: Int => i.toString
    case l: Long => l.toString
    case f: Float => f.toString
    case d: Double => d.toString
    case xs: List[_] => xs.map(renderListElem).mkString("[", ", ", "]")
    case null => "null"
    case other => other.toString
}
