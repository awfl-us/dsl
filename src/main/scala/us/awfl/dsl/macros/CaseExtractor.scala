package us.awfl.dsl.macros

import us.awfl.dsl.{BaseValue, Value}

/**
 * Public entry point for extracting the matched value for each branch of a
 * literal PartialFunction and pairing it with the branch result.
 *
 * Usage (supported patterns only: singleton object, simple case class, literal, wildcard):
 *
 *   matchedCases(statusValue) {
 *     case Pending => "pending"
 *     case Failed(code, reason) => s"$code:$reason"
 *   }
 *
 * Semantics
 * - Returns a List[(BaseValue[A], B)] containing one entry per case that matches the provided `value`.
 * - The left side is constructed without requiring a concrete A instance:
 *     - true compile-time constants (literals/modules) are wrapped via obj(constant)
 *     - otherwise we use the inbound Value[A] directly or field access via value.flatMap(_.fieldName)
 * - Order is preserved (top-to-bottom as written by the user).
 * - If no cases match, returns Nil.
 */
trait CaseExtractor:
  inline def matchedCases[A, B](inline value: Value[A])(inline pf: PartialFunction[A, B]): List[(BaseValue[A], B)] =
    Macros.matchedCases[A, B](value)(pf)
