package scala.tools.abide.traversal

import scala.tools.abide._
import scala.reflect.internal._
import scala.reflect.internal.traversal._

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

class NaiveTraversalAnalyzer(val universe : SymbolTable, rules : List[TraversalRule]) extends Analyzer {
  import universe._

  def apply(tree : Tree) : List[Warning] = rules.flatMap { rule =>
    rule.asInstanceOf[Traversal { val universe : NaiveTraversalAnalyzer.this.universe.type }].traverse(tree)
    rule.state.warnings
  }
}
