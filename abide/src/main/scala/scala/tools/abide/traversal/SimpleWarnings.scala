package scala.tools.abide.traversal

import scala.reflect.macros._
import scala.language.experimental.macros
import scala.tools.abide.traversal._

/**
 * IncrementalWarnings
 *
 * Base trait provides an extension to [[RuleState]] which requires a transformation
 * that adds a new warning to the accumulated set:
 * ```scala
 * def nok(warning: Warning): State
 * ```
 *
 * We also refine the [[State]] type member to require an [[IncrementalState]] to
 * guarantee all subtypes rules can register warnings with the `nok` transformer.
 */
trait IncrementalWarnings extends TraversalRule {
  import context.universe._

  /**
   * IncrementalState
   *
   * Extension to [[RuleState]] that requires a transformer to register warnings
   */
  trait IncrementalState extends RuleState {
    def nok(warning: Warning): State
  }

  /**
   * The state type we'll be dealing with in subtype rules, required to be a subtype
   * of the [[IncrementalState]] trait.
   *
   * @see [[IncrementalState]]
   */
  type State <: IncrementalState
}

/**
 * SimpleWarnings
 *
 * Mixin trait for [[TraversalRule]] subtypes that lets users define warning
 * messages with little to no boilerplate required. The trait provides an
 * implicit StringContext wrapper that enables users to define the warning
 * message simply as a string interpolation:
 * ```scala
 * val warning = w"This is a warning"
 * ```
 *
 * Furthermore, the trait leverages the [[WarningExtractor.w]] macro to let
 * users refer to the current tree the rule is visiting when registering a new
 * warning:
 * ```scala
 * val warning = w"The code $tree is baaad"
 * ```
 *
 * The [[warning]] definition type is a function of `Tree => String` so other
 * warning definitions than string interpolations are also possible.
 */
trait SimpleWarnings extends IncrementalWarnings with WarningExtractor {
  import context.universe._

  /** provide the Warning type member of [[scala.tools.abide.Rule]] */
  final class Warning(val tree: Tree, val message: String) extends RuleWarning {
    val pos = tree.pos
  }

  /**
   * WarningGenerator
   *
   * A function wrapper that provides some string transformations such
   * as `stripMargin` that will be applied to the generatd message.
   */
  protected implicit class WarningGenerator(val f: Tree => String) extends (Tree => String) {
    def apply(tree: Tree): String = f(tree)

    /**
     * stripMargin transformation applied on the generated warning message
     *
     * @see scala.collection.immutable.StringOps.stripMargin
     */
    def stripMargin: WarningGenerator = new WarningGenerator(tree => f(tree).stripMargin)
  }

  /** The warning message generator, either a function or a `w` string interpolation */
  val warning: Tree => String

  /**
   * A helper function that lets users register warnings given their tree
   * The [[warning]] function is used to transform the tree into a valid message.
   */
  def nok(tree: Tree): Unit = { transform(_ nok (new Warning(tree, warning(tree)))) }
}
