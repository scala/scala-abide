package scala.tools.abide
package rules

import reflect.runtime.universe._

trait ValInsteadOfVar extends ExistentialRule {
  type Elem = analyzer.global.Symbol
}

class LocalValInsteadOfVar(val analyzer : Analyzer) extends ValInsteadOfVar {
  import analyzer._
  import global._

  val name = "local-val-instead-of-var"

  val step = optimize {
    case varDef @ q"$mods var $name : $tpt = $value" if varDef.symbol.owner.isMethod =>
      nok(varDef.symbol)
    case q"$rcv = $expr" =>
      ok(rcv.symbol)
  }
}

class MemberValInsteadOfVar(val analyzer : Analyzer) extends ValInsteadOfVar {
  import analyzer._
  import global._

  val name = "member-val-instead-of-var"

  val step = optimize {
    case varDef @ q"$mods var $name : $tpt = $value" =>
      val setter : Symbol = varDef.symbol.setter
      if (setter.isPrivate) nok(varDef.symbol) else maintain
    case set @ q"$setter(..$args)" if setter.symbol.isSetter =>
      ok(setter.symbol.accessed)
  }
}
