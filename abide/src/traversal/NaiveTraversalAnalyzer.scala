package scala.tools.abide.traversal

import scala.tools.nsc._
import scala.tools.abide._
import scala.reflect.internal.traversal._

object NaiveTraversalAnalyzerGenerator extends AnalyzerGenerator {
  def mkAnalyzer(global : Global, rules : List[Rule]) : NaiveTraversalAnalyzer = {
    val traversalRules = rules.map(_ match {
      case t : TraversalRule => t
      case rule => scala.sys.error("Unexpected rule type for TraversalAnalyzer : " + rule.getClass)
    })

    new NaiveTraversalAnalyzer(global, traversalRules)
  }

  val subsumes = Set.empty[AnalyzerGenerator]
}

class NaiveTraversalAnalyzer(val global : Global, rules : List[TraversalRule]) extends Analyzer {
  import global._

  def apply(tree : Tree) : List[Warning] = rules.flatMap { rule =>
    val state = rule.asInstanceOf[Traversal { val universe : global.type }].traverse(tree)
    state.asInstanceOf[rule.State].warnings
  }
}
