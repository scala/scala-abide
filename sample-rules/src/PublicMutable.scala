package com.typesafe.abide.sample

import scala.tools.abide._
import scala.tools.abide.traversal._
import scala.tools.abide.directives._

object PublicMutable extends ContextGenerator {
  def mkContext(global : scala.tools.nsc.Global) = new Context(global) with MutabilityChecker
}

class PublicMutable(val context : Context with MutabilityChecker) extends WarningRule {
  import context._
  import global._

  val name = "public-mutable-fields"

  case class Warning(tree : Tree) extends RuleWarning {
    val pos = tree.pos
    val message = {
      val name = tree.asInstanceOf[ValDef].name
      s"Mutability should be encapsulated but mutable value $name is part of the public API"
    }
  }

  val step = optimize {
    case valDef @ q"$mods val $name : $tpt = $value" if !mods.isSynthetic && tpt.tpe != null =>
      val getter : Symbol = valDef.symbol.getter
      val owner : Symbol = valDef.symbol.owner
      if (getter.isPublic && (owner.isClass || owner.isModule) && publicMutable(tpt.tpe)) nok(Warning(valDef)) else maintain

    case varDef @ q"$mods var $name : $tpt = $value" if !mods.isSynthetic =>
      val getter : Symbol = varDef.symbol.getter
      val owner : Symbol = varDef.symbol.owner
      if (getter.isPublic && (owner.isClass || owner.isModule)) nok(Warning(varDef)) else maintain
  }
}
