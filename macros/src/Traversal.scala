package scala.tools.abide
package traversal

/**
 * Traversal
 * 
 * Fusable traversal base class
 * 
 * Declared in seperate context from FusingTraversals to align with Abide rules
 * so these can be defined in their own file (nicer for users, but makes global
 * tracking complexe).
 *
 * @see FusingTraversals
 */
trait Traversal {
  val analyzer : FusingTraversals
  import analyzer._
  import global._

  /**
   * Step type, should return something like a PartialFunction[Tree,State]
   * This must remain a type member so that TraversalMacros can specify optimizers
   * on different step types.
   *
   * @see TraversalMacros
   */
  type Step

  /**
   * Actual step value.
   * Must be a value member of the traversal class since the optimizer macros
   * need to inject their discoveries somewhere (ie. member of the Step type)
   */
  val step : Step

  /** Meat and potatos of step application (since we don't actually know the exact
    * shape of the step function at this point)
    * However, we DO know that we'll be transforming state on tree visit (ie. args)
    */
  def apply(tree : Tree, state : State) : Option[(State, Option[State => State])]


  /**
   * State type that is trundled along during traversal (no constraints since
   * TraversalSteps will actually act on the state type.
   *
   * @see TraversalStep
   * @see FusingTraversals
   */
  type State

  /** Initial state value used during traversal */
  def emptyState : State

  private def fused : FusedTraversal = new FusedTraversal {
    def traversals = Seq(Traversal.this.asInstanceOf[TraversalType])
    def emptyStates = Seq(Traversal.this.emptyState)
  }

  def fuse(traversals : TraversalType*) : FusedTraversal = traversals.foldLeft(fused) {
    (acc, traversal) => acc.fuse(traversal)
  }

  def traverse(tree : Tree) : State = {
    fused.traverse(tree)(Traversal.this.asInstanceOf[TraversalType]).asInstanceOf[State]
  }
}
