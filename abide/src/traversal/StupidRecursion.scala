package scala.tools.abide
package traversal

import reflect.runtime.universe._

class StupidRecursion(val analyzer : TraversalAnalyzer) extends ScopingRule {
  import analyzer.global._

  type Elem = Symbol

  val name = "stupid-recursion"

  val step = optimize {
    state => {
      case defDef @ q"def $name : $tpt = $body" => enter(defDef.symbol)
      case id @ Ident(_) if id.symbol != null && (state in id.symbol) => nok(id.pos)
      case s @ Select(_, _) if s.symbol != null && (state in s.symbol) => nok(s.pos)
    }
  }
}
