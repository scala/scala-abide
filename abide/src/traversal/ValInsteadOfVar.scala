package scala.tools.abide
package traversal

import reflect.runtime.universe._

trait ValInsteadOfVar extends ExistentialRule {
  type Key = analyzer.global.Symbol
}

class LocalValInsteadOfVar(val analyzer : TraversalAnalyzer) extends ValInsteadOfVar {
  import analyzer.global._

  val name = "local-val-instead-of-var"

  case class Warning(tree : Tree) extends RuleWarning {
    val pos = tree.pos
    val message = s"The `var` $tree was never assigned locally and should therefore be declared as a `val`"
  }

  val step = optimize {
    case varDef @ q"$mods var $name : $tpt = $value" if varDef.symbol.owner.isMethod =>
      nok(varDef.symbol, Warning(varDef))
    case q"$rcv = $expr" =>
      ok(rcv.symbol)
  }
}

class MemberValInsteadOfVar(val analyzer : TraversalAnalyzer) extends ValInsteadOfVar {
  import analyzer.global._

  val name = "member-val-instead-of-var"

  case class Warning(tree : Tree) extends RuleWarning {
    val pos = tree.pos
    val message = s"The member `var` $tree was never assigned locally and should therefore be declared as a `val`"
  }

  val step = optimize {
    case varDef @ q"$mods var $name : $tpt = $value" =>
      val setter : Symbol = varDef.symbol.setter
      if (setter.isPrivate) nok(varDef.symbol, Warning(varDef)) else maintain
    case set @ q"$setter(..$args)" if setter.symbol.isSetter =>
      ok(setter.symbol.accessed)
  }
}
