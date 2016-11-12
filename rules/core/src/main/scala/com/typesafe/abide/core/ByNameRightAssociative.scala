package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class ByNameRightAssociative(val context: Context) extends WarningRule with SimpleWarnings {
  import context.universe._

  val warning = w"By-name parameters will be evaluated eagerly when used in right-associative infix operators. For more details, see SI-1980."

  def isByName(param: Symbol) = param.tpe.typeSymbol == definitions.ByNameParamClass

  val step = optimize {
    case defDef @ DefDef(_, name, _, params :: _, _, _) =>
      if (!treeInfo.isLeftAssoc(name.decodedName) && params.exists(p => isByName(p.symbol))) {
        nok(defDef)
      }
  }
}
