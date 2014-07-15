package com.typesafe.abide.sample

import scala.tools.abide._
import scala.tools.abide.traversal._

class RenamedDefaultParameter(val context : Context) extends WarningRule {
  import context.universe._

  val name = "renamed-default-parameter"

  case class Warning(tree : Tree) extends RuleWarning {
    val pos = tree.pos
    val message = "Renaming parameters with default values can lead to unexpected behavior"
  }

  val step = optimize {
    case defDef : DefDef =>
      defDef.symbol.overrides.foreach { overriden =>
        val names = overriden.asMethod.paramLists.flatten.map(_.name).toSet
        (defDef.vparamss.flatten zip overriden.asMethod.paramLists.flatten).foreach { case (vd, o) =>
          if (vd.symbol.isParamWithDefault && vd.symbol.name != o.name && names(vd.symbol.name)) {
            nok(Warning(vd))
          }
        }
      }
  }
}

