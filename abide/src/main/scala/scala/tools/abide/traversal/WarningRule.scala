package scala.tools.abide.traversal

import scala.reflect.macros._
import scala.language.experimental.macros

import scala.reflect.internal.traversal._

/**
 * WarningRule
 *
 * TraversalRule subtrait that collects warnings based on local context only. We extend the [[IncrementalWarnings]] mixin trait
 * to get simple warning definition with [[SimpleWarnings]] and reduce boilerplate for such rules.
 *
 * Since verification is run after typer, such rules are either quite simple and purely stylistic (public/private considerations) or
 * rely on tree.symbol information to provides a certain non-locallity to tree information (like overrides, overloads).
 */
trait WarningRule extends TraversalRule with IncrementalWarnings {
  import context.universe._

  def emptyState = State(Nil)
  case class State(warnings: List[Warning]) extends IncrementalState {
    /** required by [[IncrementalState]] */
    def nok(warning: Warning): State = State(warning :: warnings)
  }

  /** Reports a warning */
  def nok(warning: Warning): Unit = { transform(_ nok warning) }
}
