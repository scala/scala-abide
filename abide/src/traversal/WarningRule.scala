package scala.tools.abide
package traversal

trait WarningRule extends SimpleTraversal with TraversalRule { self : Traversal =>
  import analyzer.global._

  type State = SimpleState

  def emptyState = new SimpleState(Set.empty)

  case class SimpleWarning(rule : Rule, pos : Position) extends Warning
  case class SimpleState(issues : Set[Position]) extends scala.tools.abide.State {
    def warnings = issues.map(new SimpleWarning(WarningRule.this, _)).toList

    def nok(pos : Position) : SimpleState = new SimpleState(issues + pos)
  }

  def nok(pos : Position) : TraversalStep[Tree, State] = new SimpleStep[Tree, State] {
    val enter = (state : State) => state nok pos
  }
}


// vim: set ts=4 sw=4 et:
