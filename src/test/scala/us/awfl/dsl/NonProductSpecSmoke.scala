package us.awfl.dsl

import us.awfl.dsl.auto.given

/** The historical non-product fallback is an intentional lazy failure.
  * It allows opaque ADT value references and fails only on unsupported `.get`.
  */
object NonProductSpecSmoke:
  sealed trait Kala
  case class SegKala(session: Value[String]) extends Kala
  case class WeekKala(week: Value[Int]) extends Kala
  case class Wrapped(kala: Value[Kala])
  case class InvalidEager(kala: Kala)

  def main(args: Array[String]): Unit =
    val opaque = Value[Kala]("kala")
    val list = ListValue[Kala]("kalas")
    assert(opaque.resolver.path.path.nonEmpty)
    assert(list.resolver.path.path.nonEmpty)

    // Concrete products are still derived, including nested opaque Value[T].
    val concrete = Value[Wrapped]("holder").get
    assert(concrete.kala.resolver.path.path.length == 2)

    // A direct projection of the sum type cannot safely pick a subtype.
    val ex = intercept(opaque.get)
    assert(ex.getMessage.contains("No Spec for non-product type"))
    assert(ex.getMessage.contains("Match"))

    // A non-product field through a product must also fail on projection.
    val nested = intercept(Value[InvalidEager]("eager").get)
    assert(nested.getMessage.contains("No Spec for non-product type"))

    // Existing unsafe access guard must remain higher priority for primitives.
    val primitive = intercept(Value[String]("name").get)
    assert(primitive.getMessage.contains("Unsafe value get"))
    println("DSL_NONPRODUCT_SPEC_PASS")

  private def intercept(action: => Any): RuntimeException =
    try
      action
      throw new AssertionError("Expected failure, but projection succeeded")
    catch
      case e: RuntimeException => e
