package scala.tools.abide.traversal

import scala.reflect.internal.traversal._

trait WarningRule extends TraversalRule {
  import context.universe._

  def emptyState = State(Nil)
  case class State(warnings : List[Warning]) extends RuleState {
    def nok(warning : Warning) : State = State(warning :: warnings)
  }

  def nok(warning : Warning) { transform(_ nok warning) }

}


// vim: set ts=4 sw=4 et:
