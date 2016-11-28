package com.lightbend.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._
import scala.tools.abide.directives._
import scala.reflect.internal._

object PublicMutable extends ContextGenerator {
  def getContext(universe: SymbolTable) = new Context(universe) with MutabilityChecker
}

class PublicMutable(val context: Context with MutabilityChecker) extends WarningRule {
  import context._
  import universe._

  val name = "public-mutable-fields"

  abstract class Warning(val tree: Tree) extends RuleWarning {
    val pos = tree.pos
    val name = tree.asInstanceOf[ValDef].name.toString.trim
  }

  case class ValWarning(vd: Tree, witness: MutableWitness) extends Warning(vd) {
    val message = s"${name} is a visible mutable `val`, since ${witness.withPath(Seq(name))}"
  }

  case class VarWarning(vd: Tree) extends Warning(vd) {
    val message = s"${name} is a public `var`, which is probably not a good idea"
  }

  val step = optimize {
    case valDef @ q"$mods val $name : $tpt = $value" if !mods.isSynthetic && tpt.tpe != null =>
      if (valDef.symbol.hasGetter) { // private[this] fields have no getters, yet are obviously private
        val getter: Symbol = valDef.symbol.getter
        val owner: Symbol = valDef.symbol.owner
        if (getter.isPublic && (owner.isClass || owner.isModule)) publicMutable(tpt.tpe) match {
          case witness: MutableWitness => nok(ValWarning(valDef, witness))
          case _                       =>
        }
      }

    case varDef @ q"$mods var $name : $tpt = $value" if !mods.isSynthetic =>
      if (varDef.symbol.hasGetter) { // private[this] fields have no getters, yet are obviously private
        val getter: Symbol = varDef.symbol.getter
        val owner: Symbol = varDef.symbol.owner
        if (getter.isPublic && (owner.isClass || owner.isModule)) nok(VarWarning(varDef))
      }
  }
}
