package us.awfl.dsl.macros

import scala.quoted.*
import us.awfl.dsl.{BaseValue, Value}

object Macros:
  inline def matchedCases[A, B](inline value: Value[A])(inline pf: PartialFunction[A, B]): List[(BaseValue[A], B)] =
    ${ MacrosImpl.matchedCasesImpl[A, B]('value)('pf) }
