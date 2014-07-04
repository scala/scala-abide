package com.typesafe.abide.sample

import scala.tools.abide._
import scala.tools.abide.traversal._

class StupidRecursion(val context : Context) extends ScopingRule {
  import context.universe._

  type Owner = Symbol

  val name = "stupid-recursion"

  case class Warning(tree : Tree) extends RuleWarning {
    val pos = tree.pos
    val message = s"The value $tree is recursively used in it's directly defining scope"
  }

  val step = optimize {
    case defDef @ q"def $name : $tpt = $body" => enter(defDef.symbol)
    case id @ Ident(_) if id.symbol != null && (state in id.symbol) => nok(Warning(id))
    case s @ Select(_, _) if s.symbol != null && (state in s.symbol) => nok(Warning(s))
  }
}
