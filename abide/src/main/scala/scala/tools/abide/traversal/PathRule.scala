package scala.tools.abide.traversal

import scala.reflect.internal.traversal._

/**
 * PathRule
 *
 * TraversalRule subtrait that provides helper methods to manage hierarchical path-dependent traversal.
 * The [[enter]] method will add elements to the path state, and [[State.matches]] verifies whether a
 * certain path is indeed contained in the path state.
 *
 * As in [[WarningRule]] and [[ScopingRule]], warnings are determined given local context and are simply
 * collected with the [[nok]] method.
 */
trait PathRule extends TraversalRule with ScopingTraversal {
  import context.universe._

  /** Path type that will be accumulated in the path stack */
  type Element

  def emptyState = State(Nil, Nil)
  case class State(path: List[Element], warnings: List[Warning]) extends RuleState {
    def nok(warning: Warning): State = State(path, warning :: warnings)
    def enter(element: Element): State = State(element :: path, warnings)

    private[PathRule] def leave: State = State(path.tail, warnings)

    /** Provides access to the last element that was registered in the path */
    def last: Option[Element] = path.headOption

    /**
     * Checks whether the accumulated path contains the provided sequence.
     *
     * The element sequence is checked in order, but other elements can exist between the elements
     * we're interested in in the actual path.
     */
    def matches(seq: Element*): Boolean = seq.reverse.foldLeft[Option[List[Element]]](Some(path)) {
      (remaining, elem) =>
        remaining.flatMap { elements =>
          val dropped = elements.dropWhile(_ != elem)
          if (dropped.isEmpty) None else Some(dropped.tail)
        }
    }.isDefined
  }

  /** Register element as latest path element traversed (pushes onto path stack) */
  def enter(element: Element): Unit = { transform(_ enter element, _.leave) }

  /** Report a warning */
  def nok(warning: Warning): Unit = { transform(_ nok warning) }
}
