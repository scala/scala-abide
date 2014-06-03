package scala.tools.abide
package rules

import traversal._

trait ExistentialRule extends SimpleTraversalRule {
  import analyzer._
  import global._

  type Elem

  type RuleState = ValidationState

  def emptyState = ValidationState(Map.empty[Elem, Boolean])

  case class ExistentialWarning(rule: Rule, issue: Elem) extends Warning {
    override def toString : String = issue.toString
  }

  case class ValidationState(map : Map[Elem, Boolean]) extends State {
    val issues : Set[Elem] = map.filter(!_._2).map(_._1).toSet
    def warnings : List[Warning] = issues.toList.map(ExistentialWarning(ExistentialRule.this, _))

    def ok(elem : Elem) : ValidationState = ValidationState(map + (elem -> true))
    def nok(elem : Elem) : ValidationState = ValidationState(map + (elem -> map.getOrElse(elem, false)))
  }

  def ok(elem : Elem) : TraversalStep[Tree, ValidationState] = new SimpleStep[Tree, ValidationState] {
    val enter = (state : ValidationState) => state ok elem
  }

  def nok(elem : Elem) : TraversalStep[Tree, ValidationState] = new SimpleStep[Tree, ValidationState] {
    val enter = (state : ValidationState) => state nok elem
  }

}
