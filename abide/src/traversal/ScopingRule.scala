package scala.tools.abide
package traversal

trait ScopingRule extends HierarchicTraversal with TraversalRule {
  import global._

  type Owner

  def emptyState = State(Nil, Nil)
  case class State(scope : List[Owner], warnings : List[Warning]) extends RuleState {
    def nok(warning : Warning) : State = State(scope, warning :: warnings)
    def enter(owner : Owner) : State = State(owner :: scope, warnings)
    def leave : State = State(scope.tail, warnings)
    def in(owner : Owner) : Boolean = scope.nonEmpty && owner == scope.head
  }

  def enter(owner : Owner) : TraversalStep[Tree, State] = new TraversalStep[Tree, State] {
    val enter = (state : State) => state enter owner
    val leave = Some((state : State) => state.leave)
  }

  def nok(warning : Warning) : TraversalStep[Tree, State] = new SimpleStep[Tree, State] {
    val enter = (state : State) => state nok warning
  }

}
