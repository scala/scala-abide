package com.typesafe.abide.sample

import scala.tools.abide._
import scala.tools.abide.traversal._

class RenamedDefaultParameter(val context : Context) extends WarningRule {
  import context.global._

  val name = "renamed-default-parameter"

  case class Warning(tree : Tree) extends RuleWarning {
    val pos = tree.pos
    val message = "Renaming parameters with default values can lead to unexpected behavior"
  }

  val step = optimize {
    case defDef : DefDef =>
      defDef.symbol.overrides.foldLeft(maintain) { (state, overriden) =>
        val names = overriden.asMethod.paramLists.flatten.map(_.name).toSet
        (defDef.vparamss.flatten zip overriden.asMethod.paramLists.flatten).foldLeft(state) { case (state, (vd, o)) =>
          if (vd.symbol.isParamWithDefault && vd.symbol.name != o.name && names(vd.symbol.name)) {
            state and nok(Warning(vd))
          } else {
            state
          }
        }
      }
  }
}

