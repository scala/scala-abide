package scala.tools.abide.presentation

import scala.tools.nsc._
import scala.tools.abide._

/**
 * ConsolePresenterGenerator
 *
 * @see [[ConsolePresenter]]
 */
object ConsolePresenterGenerator extends PresenterGenerator {
  def getPresenter(global: Global): ConsolePresenter = {
    new ConsolePresenter(global)
  }
}

/**
 * ConsolePresenter
 *
 * Simple [[Presenter]] that outputs warnings as compiler warnings
 */
class ConsolePresenter(protected val global: Global) extends Presenter {
  import global._

  /** Outputs Abide warnings as compiler warnings */
  def apply(unit: CompilationUnit, warnings: List[Warning]): Unit = {
    warnings.foreach { warning =>
      global.warning(warning.pos, warning.message)
    }
  }
}
