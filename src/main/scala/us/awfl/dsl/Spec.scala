package us.awfl.dsl

import scala.deriving.Mirror
import scala.compiletime.{erasedValue, summonInline, constValueTuple}

case class Spec[T](init: Resolver => T)

implicit val stringSpec: Spec[String] = Spec(_ => "")
implicit val intSpec: Spec[Int] = Spec(_ => 0) // added support for Int
implicit val doubleSpec: Spec[Double] = Spec(_ => 0.0) // added support for Double
implicit val boolSpec: Spec[Boolean] = Spec(_ => false)
implicit def mapSpec[T]: Spec[Map[String, T]] = Spec(_ => Map())
implicit def listSpec[T]: Spec[List[T]] = Spec(_ => List.empty)
implicit def optValueSpec[T: Spec]: Spec[OptValue[T]] = Spec(resolver => OptResolved(resolver))
implicit def optListSpec[T: Spec]: Spec[OptList[T]] = Spec(resolver => OptList(resolver))

object auto:
  inline given derivedSpec[T](using m: Mirror.ProductOf[T]): Spec[T] =
    val labels = constValueTuple[m.MirroredElemLabels].productIterator.map(_.toString).toList
    val builders = summonSpecs[m.MirroredElemTypes](labels)

    Spec { resolver =>
      val values = builders.map(build => build(resolver))
      m.fromProduct(Tuple.fromArray(values.toArray))
    }

  inline def summonSpecs[Elems <: Tuple](labels: List[String]): List[Resolver => Any] =
    inline erasedValue[Elems] match
      case _: EmptyTuple => Nil
      case _: (t *: ts) =>
        val fieldName = labels.head
        val headFn: Resolver => Any = summonInline[SpecBuilder[t]].build(fieldName)
        headFn :: summonSpecs[ts](labels.tail)

  trait SpecBuilder[T]:
    def build(fieldName: String): Resolver => T

  object SpecBuilder extends LowPrioritySpecBuilder:
    given fieldBuilder: SpecBuilder[Field] with
      def build(fieldName: String): Resolver => Field = _.field(fieldName)

    given stringBuilder: SpecBuilder[String] with
      def build(fieldName: String): Resolver => String = _ => "" // or better: _.string(fieldName)

    given intBuilder: SpecBuilder[Int] with
      def build(fieldName: String): Resolver => Int = _ => 0

    given doubleBuilder: SpecBuilder[Double] with
      def build(fieldName: String): Resolver => Double = _ => 0.0

    given boolBuilder: SpecBuilder[Boolean] with
      def build(fieldName: String): Resolver => Boolean = _ => false

    given listFieldBuilder[T: Spec]: SpecBuilder[ListValue[T]] with
      def build(fieldName: String): Resolver => ListValue[T] = _.list(fieldName)

    given nestedValueBuilder[T: Spec]: SpecBuilder[Value[T]] with
      def build(fieldName: String): Resolver => Value[T] = _.in(fieldName)

    given nestedResolvedBuilder[T: Spec]: SpecBuilder[Resolved[T]] with
      def build(fieldName: String): Resolver => Resolved[T] = _.in(fieldName)

    given productBuilder[T](using Mirror.ProductOf[T], Spec[T]): SpecBuilder[T] with
      def build(fieldName: String): Resolver => T = _.in(fieldName).get

    given valueBuilder[T: Spec]: SpecBuilder[BaseValue[T]] with
      def build(fieldName: String): Resolver => BaseValue[T] = _.in(fieldName)

    given mapBuilder[V]: SpecBuilder[Map[String, V]] with
      def build(fieldName: String): Resolver => Map[String, V] =
        _ => Map.empty[String, V]

    given optionBuilder[T: Spec]: SpecBuilder[Option[T]] with
      def build(fieldName: String): Resolver => Option[T] = r => Some(r.in(fieldName).get)

  trait LowPrioritySpecBuilder:
    given specBasedBuilder[T](using spec: Spec[T]): SpecBuilder[T] with
      def build(fieldName: String): Resolver => T = _.in(fieldName).get

    given leafBaseValue: SpecBuilder[BaseValue[_]] with
      def build(fieldName: String): Resolver => BaseValue[_] =
        r => Value[Int](r.in[Int](fieldName))
