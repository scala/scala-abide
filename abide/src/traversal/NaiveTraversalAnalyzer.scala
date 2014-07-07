package scala.tools.abide.traversal

import scala.tools.abide._
import scala.reflect.internal._
import scala.reflect.internal.traversal._

/**
 * NaiveTraversalAnalyzerGenerator
 *
 * AnalyzerGenerator that performs single-pass traversal for each given [[TraversalRule]] instance.
 * 
 * @see [[NaiveTraversalAnalyzer]]
 */
object NaiveTraversalAnalyzerGenerator extends AnalyzerGenerator {
  def generateAnalyzer(universe : SymbolTable, rules : List[Rule]) : NaiveTraversalAnalyzer = {
    val traversalRules = rules.map(_ match {
      case t : TraversalRule => t
      case rule => scala.sys.error("Unexpected rule type for TraversalAnalyzer : " + rule.getClass)
    })

    new NaiveTraversalAnalyzer(universe, traversalRules)
  }

  val subsumes = Set.empty[AnalyzerGenerator]
}

/**
 * NaiveTraversalAnalyzer
 *
 * Analyzer that applies a list of [[TraversalRule]] instances to a given universe.Tree. Unlike the
 * [[FusingTraversalAnalyzer]], this analyzer naively performs traversal for each rule separately without fusing
 * the rules together.
 *
 * This analyzer is actually never be used in practice since it is subsumed by the
 * [[FusingTraversalAnalyzer]] packaged alongside, but it serves to display how easily new analyzers can be
 * plugged in to the Abide framework (ie. simply transitively subsume the previous analyzer)
 */
class NaiveTraversalAnalyzer(val universe : SymbolTable, rules : List[TraversalRule]) extends Analyzer {
  import universe._

  def apply(tree : Tree) : List[Warning] = rules.flatMap { rule =>
    rule.asInstanceOf[Traversal { val universe : NaiveTraversalAnalyzer.this.universe.type }].traverse(tree)
    rule.state.warnings
  }
}
