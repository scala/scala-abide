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
 * Traversals are performed by a [[TraversalFusion]] instance which will automatically deal with initialization.
 *
 * Once the traversal has been performed (ie. foreach is done), the final state of the traversal can be accessed
 * through the [[result]] method. If an exception occured during traversal, a [[TraversalError]] encapsulating it
 * will be thrown when asking for traversal result.
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

  /** Errors discovered during traversal */
  case class TraversalError(pos : Position, cause : Throwable) extends RuntimeException(cause) {
    def message = s"Error occured during traversal. Cause: ${cause.getMessage}\nUse -Ydebug to view stacktrace"
  }

  private var _error : Option[TraversalError] = None

  /**
   * Safely performs stepping and deals with issues in traverser (like exception throwing)
   *
   * If an error has been thrown previously, ignore traversal (state doesn't make sense once a [[step]] has
   * failed).
   *
   * @return whether we actually applied a step (basically when step.isDefinedAt(tree) is true)
   */
  private [traversal] def apply(tree : Tree) : Boolean = {
    if (_error.isDefined) false else try {
      if (!step.isDefinedAt(tree)) false else {
        step.apply(tree)
        true
      }
    } catch {
      case t : Throwable =>
        _error = Some(TraversalError(tree.pos, t))
        false
    }
  }

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
  protected def state : State = if (_state != null) _state else {
    scala.sys.error("Attempted to access traversal state before initialization!")
  }

  /**
   * State resulting from a full traversal of a tree.
   *
   * If an error was encountered during the traversal, this error will be thrown here so the calling code can catch
   * it and deal with the error accordingly.
   */
  def result : State = if (!_error.isDefined) state else throw _error.get

  /** Updates the internal traversal state by applying function _f_ to the current internal state. */
  protected[traversal] def transform(f : State => State) {
    _state = f(state)
  }

  /**
   * Sets up traversal after a previous run (or for initial run) basically by copying the [[emptyState]] result to the traversal's
   * internal state variable
   */
  protected[traversal] def init {
    _state = emptyState
    _error = None
  }

  /* Cache only used for debugging, see [[traverse]] */
  private lazy val fused = Fuse(universe)(this.asInstanceOf[Traversal { val universe : Traversal.this.universe.type }])

  /** Perform traversal directly without fusing, mostly for testing purposes */
  def traverse(tree : Tree) {
    fused.traverse(tree)
  }
}
