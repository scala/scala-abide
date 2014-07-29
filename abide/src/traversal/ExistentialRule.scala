package scala.tools.abide.traversal

import scala.reflect.internal.traversal._

/**
 * ExistentialRule
 *
 * TraversalRule subtrait that provides helper methods to mark code constructs as invalid and then check for
 * existence of certain validators that will mark the issue as resolved. The nok(key : Key, warning : Warning) method
 * will mark the key as possibly invalid and associate warning to that concern, whereas the ok(key : Key) method
 * will mark the key as valid and any issue pertaining to that key is resolved.
 *
 * Since traversal ordering doesn't necessarily correlate with control-flow, one _cannot_ make assumptions on
 * validation/invalidation ordering. The rule definition _must_ be agnostic to which is discovered first. In this case,
 * validation is allways stronger than invalidation. In other words, once ok has been called on a key, no warning can stem from that key,
 * regardless of how many noks have taken place before/after.
 */
trait ExistentialRule extends TraversalRule {
  import context.universe._

  /** Warning key type that uniquely defines the source of a warning */
  type Key

  def emptyState = State(Map.empty)
  case class State(map : Map[Key, Option[Warning]]) extends RuleState {
    def warnings = map.flatMap(_._2).toList
    def ok(key : Key) : State = State(map + (key -> None))
    def nok(key : Key, warning : Warning) : State = State(map + (key -> map.getOrElse(key, Some(warning))))
  }

  /** Marks a key as valid forever */
  def ok(key : Key): Unit = { transform(_ ok key) }

  /**
   * Marks a key as possibly invalid until an ok is found or traversal ends (in which case the key is considered as globally invalid).
   * If such a state is reached, the warning that was assigned to the key is considered a valid warning that should be reported.
   */
  def nok(key : Key, warning : Warning): Unit = { transform(_ nok (key, warning)) }

}
