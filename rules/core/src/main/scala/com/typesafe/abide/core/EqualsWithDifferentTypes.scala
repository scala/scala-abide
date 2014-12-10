package com.typesafe.abide.core

import scala.tools.abide.Context
import scala.tools.abide.traversal.WarningRule

class EqualsWithDifferentTypes(val context: Context) extends WarningRule {

  import context.universe._

  val name = "equals-with-different-types"

  case class Warning(appl: Apply) extends RuleWarning {
    val pos = appl.pos
    val message = "equals with different types will always return false"
  }

  val step = optimize {
    case appl @ Apply(Select(lhs, TermName("$eq$eq")), List(rhs)) if !(lhs.tpe.erasure =:= rhs.tpe.erasure) =>

      val bothNumerical = rhs.tpe.typeSymbol.isNumericValueClass && lhs.tpe.typeSymbol.isNumericValueClass
      if (!bothNumerical) {
        nok(Warning(appl))
      }

  }

}
