package scala.tools.abide
package traversal

trait ExistentialRule extends SimpleTraversal with TraversalRule {
  import analyzer.global._

  type Elem

  type State = ExistentialRule.this.ValidationState

  def emptyState = ValidationState(Map.empty[Elem, Option[Position]])

  case class ExistentialWarning(rule: Rule, issue: Elem, pos: Position) extends Warning {
    override def toString : String = issue.toString
  }

  case class ValidationState(map : Map[Elem, Option[Position]]) extends scala.tools.abide.State {
    val issues : Set[(Elem, Position)] = map.filter(_._2.isDefined).map(p => p._1 -> p._2.get).toSet
    def warnings : List[Warning] = issues.toList.map(p => ExistentialWarning(ExistentialRule.this, p._1, p._2))

    def ok(elem : Elem) : ValidationState = ValidationState(map + (elem -> None))
    def nok(elem : Elem, pos : Position) : ValidationState = ValidationState(map + (elem -> map.getOrElse(elem, Some(pos))))
  }

  def ok(elem : Elem) : TraversalStep[Tree, ValidationState] = new SimpleStep[Tree, ValidationState] {
    val enter = (state : ValidationState) => state ok elem
  }

  def nok(elem : Elem, pos : Position) : TraversalStep[Tree, ValidationState] = new SimpleStep[Tree, ValidationState] {
    val enter = (state : ValidationState) => state nok (elem, pos)
  }

}
