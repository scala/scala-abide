package scala.tools.abide

import scala.tools.nsc._

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

  def apply[T <: Global#Tree](tree : T) : List[Warning] = {
    if (!tree.isInstanceOf[global.Tree]) scala.sys.error("Compiler mismatch in Analyzer")
    components.toList.flatMap { component =>
      if (component.analyzer != this)
        scala.sys.error("Analyzer mismatch in component " + component)
      val ctree = tree.asInstanceOf[component.global.Tree]
      component.apply(ctree)
    }
  }
}
