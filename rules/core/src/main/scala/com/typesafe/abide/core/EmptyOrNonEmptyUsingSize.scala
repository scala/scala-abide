package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class EmptyOrNonEmptyUsingSize(val context: Context) extends WarningRule {

  import context.universe._

  val name = "empty-nonempty-using-size"

  case class Warning(appl: Apply, empty: Boolean) extends RuleWarning {
    val pos = appl.pos
    val message = "Traversable.size is very expensive on some collections, use " +
      (if (empty) "isEmpty"
      else "nonEmpty") + "which is O(1)"
  }

  private val Size = TermName("size")
  private val Length = TermName("length")
  private val Equals = TermName("$eq$eq")
  private val NotEquals = TermName("$bang$eq")

  val step = optimize {
    case a @ Apply(Select(Select(t, Size | Length), op), List(Literal(Constant(0)))) if (op == Equals || op == NotEquals) && isTraversable(t) =>
      nok(Warning(a, op == Equals))
  }

  private def isTraversable(tree: Tree) = tree.tpe <:< typeOf[Traversable[_]]

}
