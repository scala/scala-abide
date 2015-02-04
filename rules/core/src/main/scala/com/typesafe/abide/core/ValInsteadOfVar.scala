package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

trait ValInsteadOfVar extends ExistentialRule with SimpleWarnings {
  type Key = context.universe.Symbol
}

class LocalValInsteadOfVar(val context: Context) extends ValInsteadOfVar {
  import context.universe._

  val warning = w"The `var` $tree was never assigned locally and should therefore be declared as a `val`"

  val step = optimize {
    case varDef @ q"$mods var $name : $tpt = $value" if varDef.symbol.owner.isMethod =>
      nok(varDef.symbol, varDef)
    case q"$rcv = $expr" =>
      ok(rcv.symbol)
  }
}

class MemberValInsteadOfVar(val context: Context) extends ValInsteadOfVar {
  import context.universe._

  val warning = w"The member `var` $tree was never assigned locally and should therefore be declared as a `val`"

  val step = optimize {
    case varDef @ q"$mods var $name : $tpt = $value" =>
      val setter: Symbol = varDef.symbol.setter
      if (setter.isPrivate) nok(varDef.symbol, varDef)
    case set @ q"$setter(..$args)" if setter.symbol.isSetter =>
      ok(setter.symbol.accessed)
  }
}
