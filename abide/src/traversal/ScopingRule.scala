package scala.tools.abide.traversal

import scala.reflect.internal.traversal._

/**
 * ScopingRule
 * 
 * TraversalRule subtrait that provides helper methods to manage scoping during traversal. Then [[enter]] method
 * will push the current tree to the scope and register a leaver method that will pop it once we leave that tree.
 *
 * As in [[WarningRule]], warnings are determined given local (and scoping) context in a single pass (no
 * validation/invalidation mechanism).
 */
trait ScopingRule extends TraversalRule with ScopingTraversal {
  import context.universe._

  /** Scoping type (eg. method symbol, class symbol, etc.) */
  type Owner

  def emptyState = State(Nil, Nil)
  case class State(scope : List[Owner], warnings : List[Warning]) extends RuleState {
    def nok(warning : Warning) : State = State(scope, warning :: warnings)
    def enter(owner : Owner) : State = State(owner :: scope, warnings)
    def leave : State = State(scope.tail, warnings)

    def childOf(matches : Owner => Boolean) : Boolean = scope.nonEmpty && matches(scope.head)
    def childOf(owner : Owner) : Boolean = childOf(_ == owner)

    def in(matches : Owner => Boolean) : Boolean = scope.exists(matches(_))

    def parents(matches : Owner => Boolean) : List[Owner] = scope.filter(matches(_))
    def parent : Option[Owner] = scope.headOption
  }

  /** Register owner as current scope (pushes it onto scoping stack) */
  def enter(owner : Owner) { transform(_ enter owner, _.leave) }

  /** Reports a warning */
  def nok(warning : Warning) { transform(_ nok warning) }
}
