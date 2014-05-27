package scala.tools.abide
package rules

import traversal._
import reflect.runtime.universe._

trait ValInsteadOfVar extends ExistentialRule {
  type Elem = traverser.global.Symbol
}

class LocalValInsteadOfVar(val traverser : FastTraversal) extends ValInsteadOfVar {
  import traverser._
  import global._

  val name = "local-val-instead-of-var"

  val step = next {
    case varDef @ q"$mods var $name : $tpt = $_" if varDef.symbol.owner.isMethod =>
      nok(varDef.symbol)
    case q"$rcv = $expr" =>
      ok(rcv.symbol)
  }
}

class MemberValInsteadOfVar(val traverser : FastTraversal) extends ValInsteadOfVar {
  import traverser._
  import global._

  val name = "member-val-instead-of-var"

  val step = next {
    case varDef @ q"$mods var $name : $tpt = $_" =>
      val setter : Symbol = varDef.symbol.setter
      if (setter.isPrivate) nok(varDef.symbol) else maintain
    case set @ q"$setter(..$_)" if setter.symbol.isSetter =>
      ok(setter.symbol.accessed)
  }
}
