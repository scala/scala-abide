package scala.tools.abide.traversal

import scala.reflect.macros._
import scala.language.experimental.macros
import scala.tools.abide.traversal._

/**
 * IncrementalWarnings
 *
 * Base trait that provides an extension to [[RuleState]] which requires a transformation
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

  /** Register a warning */
  def nok(warning: Warning): Unit = { transform(_ nok warning) }

  /**
   * If we've extended [[SimpleWarnings]], we can register warnings directly from
   * the tree that's causing the issue.
   *
   * XXX: better way of doing this?
   */
  def nok(tree: Tree)(implicit ev: this.type <:< SimpleWarnings): Unit = {
    val simpleWarnings = this.asInstanceOf[SimpleWarnings { val context: IncrementalWarnings.this.context.type }]
    val warning: Warning = simpleWarnings.mkWarning(tree).asInstanceOf[Warning]
    nok(warning)
  }
}

/**
 * KeyedWarnings
 *
 * Base trait that provides an extension to [[RuleState]] which requires a transformation
 * that adds a new keyed warning to the accumulated set.
 * ```scala
 * def nok(key: Key, warning: Warning): State
 * ```
 * This is typically used in rules where local information is not enough to generate
 * warnings, eg. [[ExistentialRule]].
 *
 * We also refine the [[State]] type member to require a [[KeyedState]] to
 * guarantee all subtypes rules can register warnings with the `nok` transformer.
 */
trait KeyedWarnings extends TraversalRule {
  import context.universe._

  /** Key information associated to a warning, used to validate/invalidate warnings */
  type Key

  /**
   * KeyedState
   *
   * An extension of [[RuleState]] that makes sure keyed warnings can be added to the state.
   */
  trait KeyedState extends RuleState {
    def nok(key: Key, warning: Warning): State
  }

  /**
   * The state type we'll be dealing with in subtype rules, required to be a subtype
   * of the [[KeyedState]] trait.
   *
   * @see [[KeyedState]]
   */
  type State <: KeyedState

  /** Register a keyed warning */
  def nok(key: Key, warning: Warning): Unit = { transform(_ nok (key, warning)) }

  /**
   * If we've extended [[SimpleWarnings]], we can register warnings directly from
   * the tree that's causing the issue.
   *
   * XXX: better way of doing this?
   */
  def nok(key: Key, tree: Tree)(implicit ev: this.type <:< SimpleWarnings): Unit = {
    val simpleWarnings = this.asInstanceOf[SimpleWarnings { val context: KeyedWarnings.this.context.type }]
    val warning: Warning = simpleWarnings.mkWarning(tree).asInstanceOf[Warning]
    nok(key, warning)
  }
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
trait SimpleWarnings extends TraversalRule with WarningExtractor {
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
   * Create warnings based on message creation function
   * This function gets called in IncrementalWarnings and KeyedWarnings.
   */
  private[traversal] def mkWarning(tree: Tree): Warning = new Warning(tree, warning(tree))
}
