package scala.tools.abide.traversal

import scala.reflect.internal.traversal._

trait ScopingRule extends TraversalRule with ScopingTraversal {
  import context.universe._

  type Owner

  def emptyState = State(Nil, Nil)
  case class State(scope : List[Owner], warnings : List[Warning]) extends RuleState {
    def nok(warning : Warning) : State = State(scope, warning :: warnings)
    def enter(owner : Owner) : State = State(owner :: scope, warnings)
    def leave : State = State(scope.tail, warnings)
    def in(owner : Owner) : Boolean = scope.nonEmpty && owner == scope.head
  }

  def enter(owner : Owner) { transform(_ enter owner, _.leave) }

  def nok(warning : Warning) { transform(_ nok warning) }

}
