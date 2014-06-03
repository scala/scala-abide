package scala.tools.abide

trait AnalysisComponent {
  val analyzer : Analyzer
  val global : analyzer.global.type = analyzer.global

  type RuleType <: Rule

  def computeRules : Unit

  def apply(tree : global.Tree) : List[Warning]
}
