package scala.reflect.internal.traversal

/**
 * ScopingTraversal
 *
 * Extension of the [[Traversal]] trait that provides entering _and_ leaving state transformations.
 * These enable users to maintain scoping information during traversals so one can define an
 * `is in` relation between state and trees (or symbols or whatever else one wishes to scope against).
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
  def transform(enter : State => State, leave : State => State) {
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
}
