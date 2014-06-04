package scala.tools.abide
package traversal

import reflect.runtime.universe._

trait ValInsteadOfVar extends ExistentialRule {
  type Elem = analyzer.global.Symbol
}

class LocalValInsteadOfVar(val analyzer : TraversalAnalyzer) extends ValInsteadOfVar {
  import analyzer.global._

  val name = "local-val-instead-of-var"

  val step = optimize {
    case varDef @ q"$mods var $name : $tpt = $value" if varDef.symbol.owner.isMethod =>
      nok(varDef.symbol, varDef.pos)
    case q"$rcv = $expr" =>
      ok(rcv.symbol)
  }
}

class MemberValInsteadOfVar(val analyzer : TraversalAnalyzer) extends ValInsteadOfVar {
  import analyzer.global._

  val name = "member-val-instead-of-var"

  val step = optimize {
    case varDef @ q"$mods var $name : $tpt = $value" =>
      val setter : Symbol = varDef.symbol.setter
      if (setter.isPrivate) nok(varDef.symbol, varDef.pos) else maintain
    case set @ q"$setter(..$args)" if setter.symbol.isSetter =>
      ok(setter.symbol.accessed)
  }
}
