package scala.tools.abide

import scala.tools.nsc._
import scala.tools.abide.presentation._

trait AnalyzerGenerator {
  def mkAnalyzer(g : Global, rules : List[Rule]) : Analyzer
  def subsumes : Set[AnalyzerGenerator]
}

/**
 * Analyzer
 * 
 * Container class for abide analysis that provides some simple tools for rules
 * management (like enabling and disabling). To add new rules or new verification
 * components, simply extend the Analyzer trait with the new parts.
 */
trait Analyzer {
  val global : Global
  import global._

  def apply(tree : Tree) : List[Warning]
}
