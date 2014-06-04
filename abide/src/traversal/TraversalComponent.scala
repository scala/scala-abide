package scala.tools.abide
package traversal

import scala.tools.nsc._

class TraversalComponent(val analyzer : TraversalAnalyzer) extends AnalysisComponent {
  import analyzer._
  import global._

  type RuleType = TraversalRule { val universe : TraversalComponent.this.analyzer.type }

  private var fused : Option[FusedTraversal] = None

  def computeRules {
    val traversals = analyzer.rules.flatMap { rule =>
      if (rule.analyzer != analyzer)
        scala.sys.error("Mismatch between analyzer and component!")

      if (rule.isInstanceOf[TraversalRule] && analyzer.enabled(rule.name)) {
        Some(rule.asInstanceOf[analyzer.TraversalType])
      } else {
        None
      }
    }

    if (traversals.nonEmpty) {
      val ft = fuse(traversals : _*)
      fused = Some(ft)
    } else {
      fused = None
    }
  }

  def apply(tree : Tree) : List[Warning] = fused match {
    case Some(traverser) =>
      traverser.traverse(tree).toSeq.flatMap(_.asInstanceOf[scala.tools.abide.State].warnings).toList
    case None => Nil
  }
}
