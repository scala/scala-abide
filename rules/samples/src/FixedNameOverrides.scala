package com.typesafe.abide.sample

import scala.tools.abide._
import scala.tools.abide.traversal._

class FixedNameOverrides(val context : Context) extends WarningRule {
  import context.universe._

  val name = "fixed-name-overrides"

  case class Warning(vd : ValDef, sn : String, sym : MethodSymbol) extends RuleWarning {
    val pos = vd.pos
    val message = s"Renaming parameter ${vd.name} of method ${vd.symbol.owner.name} from ${sn} in super-type ${sym.owner.name} can lead to confusion"
  }

  val step = optimize {
    case defDef : DefDef if !defDef.symbol.isSynthetic && !defDef.symbol.owner.isSynthetic =>
      defDef.symbol.overrides.foreach { overriden =>
        (defDef.vparamss.flatten zip overriden.asMethod.paramLists.flatten).foreach { case (vd, o) =>
          if (vd.symbol.name != o.name) {
            nok(Warning(vd, o.name.toString, overriden.asMethod))
          }
        }
      }
  }
}

