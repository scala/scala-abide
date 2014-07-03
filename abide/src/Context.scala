package scala.tools.abide

import scala.tools.nsc._

class Context(val global : Global)

trait ContextGenerator {
  def mkContext(global : Global) : Context
}
