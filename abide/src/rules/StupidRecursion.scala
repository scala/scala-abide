package scala.tools.abide
package rules

import reflect.runtime.universe._

class StupidRecursion(val analyzer : Analyzer) extends ScopingRule {
  import analyzer._
  import global._

  val name = "stupid-recursion"

  val step = optimize {
    state => {
      case defDef @ q"def $name : $tpt = $body" => enter(defDef.symbol)
      case id @ Ident(_) if id.symbol != null && state.in(id.symbol) => warn(id)
      case s @ Select(_, _) if s.symbol != null && state.in(s.symbol) => warn(s)
    }
  }
}
