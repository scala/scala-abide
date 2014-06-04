package scala.tools.abide

/**
 * AnalysisComponent
 * 
 * A component that can actually apply rules to a program. Rule merging and
 * caching should take place here.
 */
trait AnalysisComponent {
  val analyzer : Analyzer

  /** Actual type of rules this component will deal with */
  type RuleType <: Rule

  /** Component is responsible for selecting which rules it will deal with */
  def computeRules : Unit

  def apply(tree : global.Tree) : List[Warning]
}
