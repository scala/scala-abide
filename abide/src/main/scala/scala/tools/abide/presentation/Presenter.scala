package scala.tools.abide.presentation

import scala.tools.nsc._
import scala.tools.abide._

/**
 * Supertrait for presenter generator objects.
 *
 * The [[PresenterGenerator]] that will instantiate the [[Presenter]] necessary.
 *
 *
 * @see [[presentation.ConsolePresenterGenerator]] for a concrete example
 * @see [[Presenter]]
 */
trait PresenterGenerator {

  /**
   * Buils a new [[Presenter]]
   */
  def getPresenter(global: Global): Presenter

}

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
  protected val global: Global
  import global._

  /** Generate output from warnings produced by Abide analysis */
  def apply(unit: CompilationUnit, warnings: List[Warning]): Unit
}
