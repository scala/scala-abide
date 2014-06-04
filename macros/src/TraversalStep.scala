package scala.tools.abide
package traversal

import scala.reflect.api.Universe

/**
 * TraversalStep
 *
 * Encodes a single step in traversal
 * 
 * Each step transforms the state in the enter function and can register another
 * transformation on exit (in leave) so that scoping information can be encoded in this
 * traversal style.
 */
trait TraversalStep[T <: Universe#Tree, S] {

  /**
   * Entrance method that transforms the current traversal state for the current traversal
   * into a new state. Since traversal doesn't guarantee much structural ordering on traversal,
   * enter implementation should be very careful in the assumptions it makes on traversal
   * progress.
   */
  val enter : S => S

  /**
   * Exit method that transforms the current traversal state when the node is left. Since not
   * all traversals care about this information, we use an option value here (if none, ignore
   * node exit and simply maintain state).
   */
  val leave : Option[S => S]

  /**
   * Merge two steps into a new one (first run one and then the other)
   */
  def and(that : TraversalStep[T,S]) : TraversalStep[T,S] = new TraversalStep[T,S] {
    val enter : S => S = that.enter compose TraversalStep.this.enter
    val leave : Option[S => S] = TraversalStep.this.leave match {
      case Some(leaver1) => that.leave match {
        case Some(leaver2) => Some(leaver1 compose leaver2)
        case None => TraversalStep.this.leave
      }

      case None => that.leave
    }
  }
}

trait SimpleStep[T <: Universe#Tree, S] extends TraversalStep[T, S] {
  val leave : Option[S => S] = None
}
