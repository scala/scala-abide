package com.typesafe.abide.sample

import scala.tools.abide._
import scala.tools.abide.traversal._
import scala.tools.abide.directives._
import scala.reflect.internal._

object PublicMutable extends ContextGenerator {
  def getContext(universe : SymbolTable) = new Context(universe) with MutabilityChecker
}

class PublicMutable(val context : Context with MutabilityChecker) extends WarningRule {
  import context._
  import universe._

  val name = "public-mutable-fields"

  abstract class Warning(val tree : Tree) extends RuleWarning {
    val pos = tree.pos
    val name = tree.asInstanceOf[ValDef].name.toString
  }

  case class ValWarning(vd : Tree, witness : MutableWitness) extends Warning(vd) {
    val message = s"Public value ${name}is mutable, since ${witness.withPath(Seq(name))}"
  }

  case class VarWarning(vd : Tree) extends Warning(vd) {
    val message = s"${name}is a public `var`, which probably not a good idea"
  }

  val step = optimize {
    case valDef @ q"$mods val $name : $tpt = $value" if !mods.isSynthetic && tpt.tpe != null =>
      val getter : Symbol = valDef.symbol.getter
      val owner : Symbol = valDef.symbol.owner
      if (getter.isPublic && (owner.isClass || owner.isModule)) publicMutable(tpt.tpe) match {
        case witness : MutableWitness => nok(ValWarning(valDef, witness))
        case _ =>
      }

    case varDef @ q"$mods var $name : $tpt = $value" if !mods.isSynthetic =>
      val getter : Symbol = varDef.symbol.getter
      val owner : Symbol = varDef.symbol.owner
      if (getter.isPublic && (owner.isClass || owner.isModule)) nok(VarWarning(varDef))
  }
}
