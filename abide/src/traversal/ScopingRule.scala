package scala.tools.abide
package traversal

trait ScopingRule extends HierarchicTraversal with TraversalRule {
  import analyzer.global._

  type Elem

  type State = ScopingState

  def emptyState = new ScopingState(Nil, Set.empty)

  case class ScopingWarning(rule : Rule, pos : Position) extends Warning
  case class ScopingState(scope : List[Elem], issues : Set[Position]) extends scala.tools.abide.State {
    def warnings = issues.map(ScopingWarning(ScopingRule.this, _)).toList

    def nok(pos : Position) : ScopingState = new ScopingState(scope, issues + pos)

    def enter(elem : Elem) : ScopingState = new ScopingState(elem :: scope, issues)
    def leave : ScopingState = new ScopingState(scope.tail, issues)
    def in(elem : Elem) : Boolean = scope.nonEmpty && elem == scope.head
  }

  def enter(elem : Elem) : TraversalStep[Tree, ScopingState] = new TraversalStep[Tree, ScopingState] {
    val enter = (state : ScopingState) => state enter elem
    val leave = Some((state : ScopingState) => state.leave)
  }

  def nok(pos : Position) : TraversalStep[Tree, State] = new SimpleStep[Tree, State] {
    val enter = (state : State) => state nok pos
  }

}
