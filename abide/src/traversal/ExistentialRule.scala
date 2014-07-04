package scala.tools.abide.traversal

import scala.reflect.internal.traversal._

trait ExistentialRule extends TraversalRule {
  import context.universe._

  type Key

  def emptyState = State(Map.empty)
  case class State(map : Map[Key, Option[Warning]]) extends RuleState {
    def warnings = map.flatMap(_._2).toList
    def ok(key : Key) : State = State(map + (key -> None))
    def nok(key : Key, warning : Warning) : State = State(map + (key -> map.getOrElse(key, Some(warning))))
  }

  def ok(key : Key) { transform(_ ok key) }

  def nok(key : Key, warning : Warning) { transform(_ nok (key, warning)) }

}
