package scala.tools.abide.presentation

import scala.tools.nsc._
import scala.tools.abide._

/**
 * Presenter
 *
 * Base class for result "presentation". A presenter instance will receive warnings as input
 * from the analyzer in a given CompilationUnit and should use these to generate output.
 *
 * @see [[scala.tools.abide.Warning]]
 * @see [[ConsolePresenter]] for a concrete example
 */
trait Presenter {
  protected val global : Global
  import global._

  /** Generate output from warnings produced by Abide analysis */
  def apply(unit : CompilationUnit, warnings : List[Warning]) : Unit
}
