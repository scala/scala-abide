package scala.tools.abide.traversal

import scala.tools.abide._

import scala.tools.nsc._
import scala.reflect.internal._
import scala.reflect.internal.traversal._

/**
 * FusingTraversalAnalyzerGenerator
 *
 * AnalyzerGenerator for fused single-pass traversals (ie. [[TraversalRule]] subtypes).
 *
 * Subsumes [[NaiveTraversalAnalyzerGenerator]] since both traversal implementations are equivalent (but
 * fused traversals are faster).
 *
 * @see [[FusingTraversalAnalyzer]]
 */
object FusingTraversalAnalyzerGenerator extends AnalyzerGenerator {
  def getAnalyzer(global : Global, rules : List[Rule]) : FusingTraversalAnalyzer = {
    val traversalRules = rules.map(_ match {
      case t : TraversalRule => t
      case rule => scala.sys.error("Unexpected rule type for TraversalAnalyzer : " + rule.getClass)
    })

    new FusingTraversalAnalyzer(global, traversalRules)
  }

  val subsumes : Set[AnalyzerGenerator] = Set(NaiveTraversalAnalyzerGenerator)
}

/**
 * FusingTraversalAnalyzer
 *
 * Analyzer that applies a list of [[TraversalRule]] instances to a given universe.Tree. The traversalsr
 * specified in the TraversalRules are fused into a single-pass traversal that optimizes speed by relying
 * on a scala.reflect.internal.traversal.TraversalFusion (type based traversal performance enhancer).
 */
class FusingTraversalAnalyzer(val global : Global, rules : List[TraversalRule]) extends Analyzer {
  import global._

  private val fused : Option[TraversalFusion { val universe : FusingTraversalAnalyzer.this.global.type }] =
    if (rules.isEmpty) None else Some(Fuse(global)(rules.map { rule =>
      if (rule.context.universe != global)
        scala.sys.error("Missmatch between analyzer and rule universe")

      rule.asInstanceOf[Traversal { val universe : FusingTraversalAnalyzer.this.global.type }]
    } : _*))

  def apply(tree : Tree) : List[Warning] = fused match {
    case Some(traverser) =>
      traverser.traverse(tree)
      rules.flatMap { rule =>
        try {
          rule.result.warnings
        } catch {
          case t : rule.TraversalError =>
           global.warning(t.pos, t.message)
           global.debugStack(t.cause)
           Nil
        }
      }.toList
    case None => Nil
  }
}
