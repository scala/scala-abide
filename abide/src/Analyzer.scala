package scala.tools.abide

import scala.tools.nsc._
import presentation._

/**
 * Analyzer
 * 
 * Container class for abide analysis that provides some simple tools for rules
 * management (like enabling and disabling). To add new rules or new verification
 * components, simply extend the Analyzer trait with the new parts.
 */
trait Analyzer {
  val global : Global

  val components : Seq[AnalysisComponent]
  val rules      : Seq[Rule]

  private var _enabled : Set[String] = Set.empty
  def enabled : Set[String] = _enabled

  def enable(names : String*) : this.type = {
    val invalid = names.toSet -- rules.map(_.name)
    if (invalid.nonEmpty)
      scala.sys.error("Enabling unregistered rule(s) :\n - " + invalid.mkString("\n - "))
    _enabled ++= names
    components.foreach(_.computeRules)
    this
  }

  def enableOnly(names : String*) : this.type = {
    _enabled = Set.empty
    enable(names : _*)
  }

  def enableAll : this.type = {
    enable(rules.map(_.name) : _*)
  }

  def apply(tree : global.Tree) : List[Warning] = components.toList.flatMap {
    component => if (component.analyzer != this) {
      scala.sys.error("Analyzer mismatch in component " + component)
    } else {
      val ctree = tree.asInstanceOf[component.analyzer.global.Tree]
      component.apply(ctree)
    }
  }
}
