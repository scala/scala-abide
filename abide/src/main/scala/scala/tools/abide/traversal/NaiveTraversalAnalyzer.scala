package scala.tools.abide.traversal

import scala.tools.abide._

import scala.tools.nsc._
import scala.reflect.internal._
import scala.reflect.internal.traversal._
import scala.reflect.internal.util.NoPosition

/**
 * NaiveTraversalAnalyzerGenerator
 *
 * AnalyzerGenerator that performs single-pass traversal for each given [[TraversalRule]] instance.
 *
 * @see [[NaiveTraversalAnalyzer]]
 */
object NaiveTraversalAnalyzerGenerator extends AnalyzerGenerator {
  def getAnalyzer(global: Global, rules: List[Rule]): NaiveTraversalAnalyzer = {
    val traversalRules = rules.flatMap(_ match {
      case t: TraversalRule =>
        Some(t)
      case rule =>
        global.reporter.warning(NoPosition, "Skipping unexpected type for TraversalAnalyzer : " + rule.getClass)
        None
    })

    new NaiveTraversalAnalyzer(global, traversalRules)
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
class NaiveTraversalAnalyzer(val global: Global, rules: List[TraversalRule]) extends Analyzer {
  import global._

  def apply(tree: Tree): List[Warning] = rules.flatMap { rule =>
    rule.asInstanceOf[Traversal { val universe: NaiveTraversalAnalyzer.this.global.type }].traverse(tree)
    try {
      rule.result.warnings
    }
    catch {
      case t: rule.TraversalError =>
        global.warning(t.pos, t.message)
        global.debugStack(t.cause)
        Nil
    }
  }
}
