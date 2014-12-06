package scala.tools.abide.traversal

import scala.reflect.internal.traversal._

/**
 * ScopingRule
 *
 * TraversalRule subtrait that provides helper methods to manage symbol scoping during traversal. The [[enterScope]] method
 * will open a new scoping block and [[scope]] will push the current symbol to the scope generated in [[enterScope]].
 * Once the traversal leaves the current scoping block, it is popped to ensure scoping equivalence with scala.
 *
 * As in [[WarningRule]], warnings are determined given local (and scoping) context in a single pass (no
 * validation/invalidation mechanism).
 */
trait ScopingRule extends TraversalRule with ScopingTraversal {
  import context.universe._

  /** Type of elements we wish to add into scope (eg. method symbol, class symbol, etc.) */
  type Element

  def emptyState = State(List(Nil), Nil)
  case class State(scope: List[List[Element]], warnings: List[Warning]) extends RuleState {
    def enterScope: State = State(Nil :: scope, warnings)
    def scope(elem: Element): State = State((elem :: scope.head) :: scope.tail, warnings)
    def nok(warning: Warning): State = State(scope, warning :: warnings)

    private[ScopingRule] def leaveScope: State = State(scope.tail, warnings)

    def lookup(matcher: Element => Boolean): Option[Element] = scope.flatMap(xs => xs find matcher).headOption
  }

  /** Open new scoping context (typically will happen when encountering a Block tree */
  def enterScope(): Unit = { transform(_.enterScope, _.leaveScope) }

  /** Add an element to the current scope (will be popped when leaving scope opening point */
  def scope(elem: Element): Unit = { transform(_ scope elem) }

  /** Reports a warning */
  def nok(warning: Warning): Unit = { transform(_ nok warning) }
}
