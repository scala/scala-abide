package scala.tools.abide
package presentation

import scala.tools.nsc._

class ConsolePresenter(val global : Global) extends Presenter {
  import global._

  def apply(unit : CompilationUnit, warnings : List[Warning]) : Unit = {
    warnings.map { warning =>
      global.warning(warning.pos, warning.message)
    }
  }
}
