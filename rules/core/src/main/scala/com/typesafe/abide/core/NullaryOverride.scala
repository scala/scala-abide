package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class NullaryOverride(val context: Context) extends WarningRule {
  import context.universe._

  val name = "nullary-override"

  case class Warning(defDef: DefDef) extends RuleWarning {
    val pos = defDef.pos
    val message = "Non-nullary method overrides nullary method"
  }

  val step = optimize {
    case defDef @ DefDef(mods, name, tparams, List(List()), tpt, rhs) if defDef.symbol.asMethod.isOverride =>
      val overrides = defDef.symbol.overrides
      if (overrides.exists(_.paramLists.isEmpty)) {
        nok(Warning(defDef))
      }
  }
}
