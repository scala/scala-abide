package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class StupidRecursion(val context: Context) extends ScopingRule with SimpleWarnings {
  import context.universe._

  type Owner = Symbol

  val warning = w"The value $tree is recursively used in its directly defining scope"

  val step = optimize {
    case defDef @ q"def $name : $tpt = $body"                             => enter(defDef.symbol)
    case id @ Ident(_) if id.symbol != null && (state childOf id.symbol)  => nok(id)
    case s @ Select(_, _) if s.symbol != null && (state childOf s.symbol) => nok(s)
  }
}
