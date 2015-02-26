package scala.tools.abide.traversal

import scala.reflect.internal.traversal._

/**
 * WarningRule
 *
 * TraversalRule subtype for rules where trees can be determined as invalid given local context.
 *
 * Since verification is run after typer, such rules are either quite simple and purely stylistic (public/private considerations) or
 * rely on tree.symbol information to provides a certain non-locallity to tree information (like overrides, overloads).
 *
 * @see [[WarningRuleTraversal]]
 */
trait WarningRule extends WarningRuleTraversal {
  import context.universe._

  def emptyState = State(Nil)
  case class State(warnings: List[Warning]) extends RuleState with WarningState {
    def nok(warning: Warning): State = State(warning :: warnings)
  }
}

/**
 * WarningRuleTraversal
 *
 * TraversalRule subtrait that provides the `nok(warning: Warning)` helper method to accumulate warnings. In isolation,
 * this rule traversal is suited for rules where invalid trees can be identified without any extra context (ie. non-local
 * information). However, this trait can also be used as a mixin for more complex traversals to provide warning
 * accumulation based on context built up in the other traversal (see [[PathRule]] or [[ScopingRule]] for examples).
 */
trait WarningRuleTraversal extends TraversalRule {
  import context.universe._

  /** Type-bound on the `State` type member that ensures children provide warning accumulation capabilities */
  type State <: WarningState

  /** RuleState subtrait that guarantees warning accumulation capabilities */
  trait WarningState extends RuleState {
    def nok(warning: Warning): State
  }

  /** Reports a warning */
  def nok(warning: Warning): Unit = { transform(_ nok warning) }
}

