package com.lightbend.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class EmptyOrNonEmptyUsingSize(val context: Context) extends WarningRule {

  import context.universe._

  val name = "empty-nonempty-using-size"

  case class Warning(appl: Tree, empty: Boolean) extends RuleWarning {
    val pos = appl.pos
    val message = "Traversable.size is very expensive on some collections, use " +
      (if (empty) "isEmpty"
      else "nonEmpty") + "which is O(1)"
  }

  val step = optimize {
    case a @ q"$subj.size == 0" if isTraversable(subj)   => nok(Warning(a, empty = true))
    case a @ q"$subj.length == 0" if isTraversable(subj) => nok(Warning(a, empty = true))
    case a @ q"$subj.size != 0" if isTraversable(subj)   => nok(Warning(a, empty = false))
    case a @ q"$subj.length != 0" if isTraversable(subj) => nok(Warning(a, empty = false))
  }

  private def isTraversable(tree: Tree) = tree.tpe <:< typeOf[Traversable[_]]

}
