package scala.reflect.internal.traversal

/**
 * Traversal
 * 
 * Fusable traversal base class
 * 
 * Declared in seperate context from [[TraversalFusion]] to align with Abide rules
 * so these can be defined in their own file. This provides a nicer API for users, but makes universe
 * sharing harder. This difficulty is however completely hidden from the end-user and dealt with internally.
 *
 * @see [[TraversalFusion]]
 */
trait Traversal {
  protected[traversal] val universe : scala.reflect.api.Universe
  import universe._

  /**
   * Step function that modifies internal traversal state.
   *
   * Must be a value member of the [[Traversal]] class since the optimizer macro
   * needs to inject class discoveries somewhere (ie. it adds members to PartialFunction subtype)
   */
  val step : PartialFunction[Tree, Unit]

  /**
   * State type that is trundled along during traversal (no constraints since
   * state updates will actually act on the state type).
   *
   * @see TraversalStep
   * @see FusingTraversals
   */
  type State

  /** Initial state value used during traversal */
  def emptyState : State

  private var _state : State = _

  /**
   * Current traversal state.
   *
   * State is maintained internally to enable foreach traversals. These traversals will use the [[transform]]
   * method in each step to update the internal state. Typically, helper methods will be provided for use in the [[step]]
   * partial function so [[transform]] doesn't need to be accessed directly.
   */
  def state : State = if (_state != null) _state else {
    scala.sys.error("Attempted to access traversal state before initialization!")
  }

  /** Updates the internal traversal state by applying [[f]] to the current internal state. */
  def transform(f : State => State) {
    _state = f(state)
  }

  /**
   * Sets up traversal after a previous run (or for initial run) basically by copying the [[emptyState]] result to the traversal's
   * internal state variable
   */
  def init {
    _state = emptyState
  }

  /* Cache only used for debugging, see [[traverse]] */
  private lazy val fused = Fuse(universe)(this.asInstanceOf[Traversal { val universe : Traversal.this.universe.type }])

  /** Perform traversal directly without fusing, mostly for testing purposes */
  def traverse(tree : Tree) {
    fused.traverse(tree)
  }
}
