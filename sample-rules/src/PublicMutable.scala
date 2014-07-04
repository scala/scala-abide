package com.typesafe.abide.sample

import scala.tools.abide._
import scala.tools.abide.traversal._
import scala.tools.abide.directives._
import scala.reflect.internal._

object PublicMutable extends ContextGenerator {
  def generateContext(universe : SymbolTable) = new Context(universe) with MutabilityChecker
}

class PublicMutable(val context : Context with MutabilityChecker) extends WarningRule {
  import context._
  import universe._

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
      if (getter.isPublic && (owner.isClass || owner.isModule) && publicMutable(tpt.tpe)) nok(Warning(valDef))

    case varDef @ q"$mods var $name : $tpt = $value" if !mods.isSynthetic =>
      val getter : Symbol = varDef.symbol.getter
      val owner : Symbol = varDef.symbol.owner
      if (getter.isPublic && (owner.isClass || owner.isModule)) nok(Warning(varDef))
  }
}
