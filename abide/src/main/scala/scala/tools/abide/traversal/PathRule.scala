package scala.tools.abide.traversal

import scala.reflect.internal.traversal._

/**
 * PathRule
 *
 * TraversalRule subtrait that provides hierarchical path management from [[PathRuleTraversal]] and
 * simple warning accumulation from [[WarningRuleTraversal]]. One can build path information during
 * traversal and use it in conjunction with local context to accumulate warnings.
 *
 * @see [[PathRuleTraversal]]
 * @see [[WarningRuleTraversal]]
 */
trait PathRule extends PathRuleTraversal with WarningRuleTraversal {
  import context.universe._

  def emptyState = State(Nil, Nil)
  case class State(path: List[Element], warnings: List[Warning]) extends PathState with WarningState {
    def withPath(newPath: List[Element]): State = State(newPath, warnings)
    def nok(warning: Warning): State = State(path, warning :: warnings)
  }
}

/**
 * PathRuleTraversal
 *
 * TraversalRule subtrait that provides helper methods to manage hierarchical path-dependent traversal.
 * The [[enter]] method will add elements to the path state and `State.matches` verifies whether a
 * certain path is indeed contained in the path state.
 */
trait PathRuleTraversal extends TraversalRule with ScopingTraversal {
  import context.universe._

  /** Path type that will be accumulated in the path stack */
  type Element

  /** Type-bound on the abstract `State` type to guarantee path handling */
  type State <: PathState

  /**
   * PathState
   *
   * RuleState subtype that adds hierarchical path accumulation to the rule state. During a traversal,
   * one can add `Element` members to the current path and traversal of children trees will contain these
   * elements in the current state's path. One can check the path structure with the helper methods
   * [[last]] and [[matches]].
   */
  trait PathState extends RuleState {
    val path: List[Element]
    def withPath(path: List[Element]): State

    /** Register element to path */
    def enter(element: Element): State = withPath(element :: path)
    private[PathRuleTraversal] def leave: State = withPath(path.tail)

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
}
