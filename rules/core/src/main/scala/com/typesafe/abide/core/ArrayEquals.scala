package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class ArrayEquals(val context: Context) extends WarningRule {

  import context.universe._

  val name = "array-equals"

  case class Warning(appl: Tree) extends RuleWarning {
    val pos = appl.pos
    val message = "equals for Arrays is not a deep equals and will always perform instance equality (see the implicitly available ArrayOps.sameElements for a deep equals)"
  }

  val step = optimize {
    case appl @ q"$lhs == $rhs" if isArray(lhs) && isArray(rhs) =>
      nok(Warning(appl))

  }

  private def isArray(tree: Tree) = tree.tpe <:< typeOf[Array[_]]

}
