package scala.tools.abide.traversal

import scala.tools.nsc._

import scala.tools.abide._
import scala.reflect.internal.traversal._

object TraversalAnalyzerGenerator extends AnalyzerGenerator {
  def mkAnalyzer(global : Global, rules : List[Rule]) : TraversalAnalyzer = {
    val traversalRules = rules.map(_ match {
      case t : TraversalRule => t
      case rule => scala.sys.error("Unexpected rule type for TraversalAnalyzer : " + rule.getClass)
    })

    println(rules.map(_.name))
    new TraversalAnalyzer(global, traversalRules)
  }

  val subsumes = Set.empty[AnalyzerGenerator]
}

class TraversalAnalyzer(val global : Global, rules : List[TraversalRule]) extends Analyzer {
  import global._

  private val fused : Option[FusedTraversal { val universe : TraversalAnalyzer.this.global.type }] =
    if (rules.isEmpty) None else Some(FusedTraversal(global)(rules.map { rule =>
      if (rule.context.global != global)
        scala.sys.error("Missmatch between analyzer and rule compilers")

      rule.asInstanceOf[Traversal { val universe : global.type }]
    } : _*))

  def apply(tree : Tree) : List[Warning] = fused match {
    case Some(traverser) =>
      val result = traverser.traverse(tree)
      (traverser.traversals zip result.toSeq).toList.flatMap {
        case (rule : TraversalRule, state) => state.asInstanceOf[rule.State].warnings
      }
    case None => Nil
  }
}
