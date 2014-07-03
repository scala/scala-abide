package scala.tools.abide.traversal

import scala.reflect.internal.traversal._

trait ExistentialRule extends SimpleTraversal with TraversalRule {
  import context.global._

  type Key

  def emptyState = State(Map.empty)
  case class State(map : Map[Key, Option[Warning]]) extends RuleState {
    def warnings = map.flatMap(_._2).toList
    def ok(key : Key) : State = State(map + (key -> None))
    def nok(key : Key, warning : Warning) : State = State(map + (key -> map.getOrElse(key, Some(warning))))
  }

  def ok(key : Key) : TraversalStep[Tree, State] = new SimpleStep[Tree, State] {
    val enter = (state : State) => state ok key
  }

  def nok(key : Key, warning : Warning) : TraversalStep[Tree, State] = new SimpleStep[Tree, State] {
    val enter = (state : State) => state nok (key, warning)
  }

}
