package scala.tools.abide
package traversal

trait TraversalRule extends Rule with TraversalMacros {

  val traverser : FastTraversal
  import traverser._
  import global._

  type RuleState <: State

  def emptyState : RuleState

  val step : RuleExpansion[Tree, RuleState]

  def maintain : TraversalStep[Tree, RuleState] = new SimpleStep[Tree, RuleState] {
    val enter : RuleState => RuleState = x => x
  }

  def apply(tree : Tree, state : RuleState) : Option[(RuleState, Option[RuleState => RuleState])] =
    step.function(tree, state).map(step => step.enter(state) -> step.leave)
}
