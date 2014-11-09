package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class OptionImplicit(val context: Context) extends WarningRule {
  import context.universe._

  val name = "option-implicit"

  case class Warning(val pos: Position, view: ApplyImplicitView) extends RuleWarning {
    val message = s"Suspicious application of an implicit view (${view.fun}) in the argument to Option.apply."
  }

  val step = optimize {
    case app @ Apply(tap @ TypeApply(q"scala.Option.apply", targs), List(view: ApplyImplicitView)) =>
      nok(Warning(app.pos, view))
  }
}
