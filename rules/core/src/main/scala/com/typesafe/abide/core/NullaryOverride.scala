package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class NullaryOverride(val context: Context) extends WarningRule with SimpleWarnings {
  import context.universe._

  val warning = w"Non-nullary method overrides nullary method"

  val step = optimize {
    case defDef @ DefDef(mods, name, tparams, List(List()), tpt, rhs) if defDef.symbol.asMethod.isOverride =>
      val overrides = defDef.symbol.overrides
      if (overrides.exists(_.paramLists.isEmpty)) {
        nok(defDef)
      }
  }
}
