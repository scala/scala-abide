package scala.tools.abide
package presentation

import scala.tools.nsc._

trait Presenter {
  val global : Global
  import global._

  def apply(unit : CompilationUnit, warnings : List[Warning]) : Unit
}
