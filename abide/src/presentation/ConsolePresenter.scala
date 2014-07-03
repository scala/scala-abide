package scala.tools.abide.presentation

import scala.tools.nsc._
import scala.tools.abide._

class ConsolePresenter(val global : Global) extends Presenter {
  import global._

  def apply(unit : CompilationUnit, warnings : List[Warning]) : Unit = {
    warnings.map { warning =>
      global.warning(warning.pos, warning.message)
    }
  }
}
