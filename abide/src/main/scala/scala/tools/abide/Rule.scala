package scala.tools.abide

import scala.reflect.internal._

/**
 * The bottom [[ContextGenerator]] that will be used if no other rule requires any extra context.
 *
 * @see [[ContextGenerator]]
 */
object Rule extends ContextGenerator[Context] {
  def getContext(universe: SymbolTable) = new Context(universe)
}

/**
 * Base class for all verification rules the framework will deal with
 */
trait Rule {
  /**
   * Rule context, which provides the rule with a compiler instance and more if necessary.
   *
   * @see [[Context]]
   */
  val context: Context

  /**
   * Pointer to the [[AnalyzerGenerator]] object necessary to apply this rule.
   */
  val analyzer: AnalyzerGenerator

  /**
   * Base trait for the state that each rule will work on during application.
   *
   * At any given time, a rule state must be able to produce a list of warnings. Typically,
   * this will be invoked after a full walk through a tree (or other method of rule application),
   * but to keep things clean, such assumptions should NOT be made.
   */
  trait RuleState {
    def warnings: List[Warning]
  }

  /**
   * The state type we're dealing with in this rule.
   *
   * @see [[RuleState]]
   */
  type State <: RuleState

  /**
   * Base trait for warnings each rule will emit.
   *
   * Using a common subclass makes it simpler to uniformely handle warnings, but we want a specific
   * type for each rule to tailor specific warnings for specific use-cases (eg. need info about
   * multiple trees to generate warning msg)
   *
   * We extend the [[scala.tools.abide.Warning]] base class that provides a (very) simple API for
   * warning consumers (like position and message).
   * @see [[scala.tools.abide.Warning]]
   */
  trait RuleWarning extends scala.tools.abide.Warning {
    val rule: Rule = Rule.this
  }

  /** The warning type we're dealing with in this rule. @see [[RuleWarning]] */
  type Warning <: RuleWarning

  /** We require a name field to manage rules (enable/disable) and pretty-print them */
  val name: String
}
