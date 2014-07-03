package scala.tools.abide

import scala.tools.nsc._
import scala.tools.abide.presentation._

/**
 * Supertrait for analyzer generator objects.
 *
 * Each [[Rule]] must be assigned an [[AnalyzerGenerator]] that will instantiate the [[Analyzer]] necessary
 * for rule application. One [[Analyzer]] will typically apply multiple rules.
 *
 * Sometimes, a new [[Analyzer]] type can actually apply the rules from other pre-existing
 * [[Analyzer]] types in a more optimal or general way. To enable such extension points, the
 * [[AnalyzerGenerator.subsumes]] field describes which generators can be skipped once this
 * one has been created.
 *
 * @see [[traversal.TraversalAnalyzerGenerator]] for a concrete example
 * @see [[Analyzer]]
 */
trait AnalyzerGenerator {

  /**
   * Buils a new [[Analyzer]] instance based on a compiler (scala.tools.nsc.Global), and
   * a list of rules. The [[Analyzer thus generated will then apply these rules to provided trees.
   */
  def mkAnalyzer(g : Global, rules : List[Rule]) : Analyzer

  /**
   * Subsumption mechanism that enables optimized or generalized analyzers to replace simpler ones.
   * In order to subsume (ie. replace) a given analyzer, simply add it's generator to the subsumption set.
   */
  def subsumes : Set[AnalyzerGenerator]
}

/**
 * Supertrait for [[Rule]] application classes.
 * 
 * In many cases, rules can be grouped together in a logical way to optimize tree traversal, or keep the
 * traversal logic outside of the rules. This logic should be contained inside the [[Analyzer]] class
 * that will apply it's contained rules to provided trees.
 *
 * @see [[traversal.TraversalAnalyzer]] for a concrete example
 * @see [[AnalyzerGenerator]]
 */
trait Analyzer {
  protected val global : Global
  import global._

  /** Applies the rules contained in this [[Analyzer]] to the provided tree and return a list of new warnings. */
  def apply(tree : Tree) : List[Warning]
}
