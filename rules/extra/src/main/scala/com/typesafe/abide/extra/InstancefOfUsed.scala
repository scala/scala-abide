package com.typesafe.abide.extra

import scala.tools.abide._
import scala.tools.abide.traversal._

class InstancefOfUsed(val context: Context) extends WarningRule {
  import context.universe._

  val name = "instance-of-used"

  case class Warning(tree: Tree, which: String) extends RuleWarning {
    val pos = tree.pos
    val message = s"Using $which. Consider using pattern matching instead."
  }

  private val AsInstanceOf = TermName("asInstanceOf")
  private val IsInstanceOf = TermName("isInstanceOf")
  val step = optimize {
    case appl @ Select(_, AsInstanceOf) =>
      nok(Warning(appl, "asInstanceOf"))

    case appl @ Select(_, IsInstanceOf) =>
      nok(Warning(appl, "isInstanceOf"))
  }

}
