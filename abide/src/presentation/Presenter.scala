package scala.tools.abide.presentation

import scala.tools.nsc._
import scala.tools.abide._

trait Presenter {
  val global : Global
  import global._

  def apply(unit : CompilationUnit, warnings : List[Warning]) : Unit
}
