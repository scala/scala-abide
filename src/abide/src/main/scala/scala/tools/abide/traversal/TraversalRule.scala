package scala.tools.abide
package traversal

trait SimpleTraversalRule extends TraversalRule with SimpleMacros {
  import traverser._
  import global._

  type Step = PartialFunction[Tree, TraversalStep[Tree, RuleState]]

  lazy val lifted = step.lift

  def apply(tree : Tree, state : RuleState) : Option[(RuleState, Option[RuleState => RuleState])] = {
    lifted.apply(tree).map(step => step.enter(state) -> step.leave)
  }
}

trait HierarchicTraversalRule extends TraversalRule with HierarchicMacros {
  import traverser._
  import global._

  type Step = RuleState => PartialFunction[Tree, TraversalStep[Tree, RuleState]]

  def apply(tree : Tree, state : RuleState) : Option[(RuleState, Option[RuleState => RuleState])] = {
    step.apply(state).lift.apply(tree).map(step => step.enter(state) -> step.leave)
  }
}

sealed trait TraversalRule extends Rule {

  val traverser : Traversal
  import traverser._
  import global._

  type Step
  type RuleState <: State

  def emptyState : RuleState

  val step : Step

  def maintain : TraversalStep[Tree, RuleState] = new SimpleStep[Tree, RuleState] {
    val enter : RuleState => RuleState = x => x
  }

  def apply(tree : Tree, state : RuleState) : Option[(RuleState, Option[RuleState => RuleState])]
}
