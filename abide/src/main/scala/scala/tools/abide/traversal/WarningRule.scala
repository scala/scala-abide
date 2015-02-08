package scala.tools.abide.traversal

import scala.reflect.internal.traversal._

/**
 * WarningRule
 *
 * TraversalRule subtrait that provides the nok(warning : Warning) helper method to accumulate warnings. In this rule,
 * trees can be determined as invalid without requiring any extra context (ie. non-local information).
 *
 * Since verification is run after typer, such rules are either quite simple and purely stylistic (public/private considerations) or
 * rely on tree.symbol information to provides a certain non-locallity to tree information (like overrides, overloads).
 */
trait WarningRule extends WarningRuleTraversal {
  import context.universe._

  def emptyState = State(Nil)
  case class State(warnings: List[Warning]) extends RuleState with WarningState {
    def nok(warning: Warning): State = State(warning :: warnings)
  }
}

trait WarningRuleTraversal extends TraversalRule {
  import context.universe._

  type State <: WarningState
  trait WarningState extends RuleState {
    def nok(warning: Warning): State
  }

  /** Reports a warning */
  def nok(warning: Warning): Unit = { transform(_ nok warning) }
}

