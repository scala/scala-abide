package scala.reflect.internal.traversal

/**
 * ScopingTraversal
 *
 * Extension of the [[Traversal]] trait that provides entering _and_ leaving state transformations.
 * These enable users to maintain scoping information during traversals so one can define an
 * `is in` relation between state and trees (or symbols or whatever else one wishes to scope against).
 *
 * The scoping information can be updated by using the transform(enter : State => State, leave : State => State) method
 * whose second parameter will be viewd as a "leaving function". This function will be applied to the
 * state when the traversal leaves the current node (as opposed to the first argument function which is
 * applied when the traversal enters the node).
 *
 * For example, in the tree
 *
 *            A
 *            |
 *            B
 *           / \
 *          C   D
 *
 * we have the following enter/leave sequence:
 *   enter(A) -> enter(B) -> enter(C) -> leave(C) -> enter(D) -> leave(D) -> leave(B) -> leave(A)
 *
 * With such a scheme, we can easily deal with scoping by pushing and popping scoping information with
 * the two functions given to the transform method.
 *
 * @see [[ScopingTraversalFusion]]
 */
trait ScopingTraversal extends Traversal {
  import universe._

  private var leaver : Option[State => State] = None

  /**
   * The [[ScopingTraversal]] transformer can also specify a leaving transformation that will be applied
   * to the internal state once the traversal leaves the current tree node.
   *
   * @see [[ScopingTraversalFusion]]
   */
  protected[traversal] def transform(enter : State => State, leave : State => State): Unit = {
    transform(enter)
    leaver = Some(leaver match {
      case Some(l) => leave andThen l
      case None => leave
    })
  }

  /**
   * Consumes leaver option, used by [[ScopingTraversalFusion]] to deal with leaver functions.
   *
   * @see [[ScopingTraversalFusion]]
   */
  protected[traversal] def consumeLeaver() : Option[State => State] = {
    val l = leaver
    leaver = None
    l
  }

  protected[traversal] override def init: Unit = {
    super.init
    leaver = None
  }
}
