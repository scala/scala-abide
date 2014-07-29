package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class UnusedMember(val context: Context) extends ExistentialRule {
  import context.universe._

  val name = "unused-member"
  type Key = Symbol

  case class Warning(tree: Tree) extends RuleWarning {
    val pos = tree.pos
    val message = s"${tree.symbol} is not used."
  }

  private def shouldConsider(sym: Symbol): Boolean = {
    val owner = sym.owner
    val isValueParameter = owner.isMethod && sym.isValueParameter

    val isMainArgs = isValueParameter && owner.name == TermName("main") && (owner.asMethod.paramLists match {
      case List(List(param)) => param.typeSignature == typeOf[Array[String]]
      case _                 => false
    })

    val ignore = ((isValueParameter && owner.isDeferred) // abstract method, params are never used
      || isMainArgs // args : Array[String] don't need to be used since they're a language requirement for the main
      || owner.isConstructor // right after typer constructors are empty, so their params are not used
      || sym.isSynthetic
      || sym.isConstant // must ignore constant types since these are folded during type checking
      || sym.name.containsChar('$') // synthetic names that are not marked as SYNTHETIC: `(_, _) => ???`
      || isValueParameter &&
      (owner.hasFlag(Flag.OVERRIDE) || owner.overrides.nonEmpty)) // overriding a method needs to keep all (unused) parameters

    def getter(sym: Symbol): Symbol = sym.getter
    val privateVal = if (getter(sym) != NoSymbol) getter(sym).isPrivate else sym.isPrivate

    !ignore &&
      (sym.isLocalToBlock ||
        (sym.isMethod && sym.isPrivate) ||
        ((sym.isVal || sym.isVar) && privateVal))
  }

  val step = optimize {
    case vd: ValDef if shouldConsider(vd.symbol) =>
      nok(vd.symbol, Warning(vd))

    case dd: DefDef if shouldConsider(dd.symbol) =>
      nok(dd.symbol, Warning(dd))

    case tree @ q"$pre.$name" =>
      ok(tree.symbol)

    case b: Bind =>
      nok(b.symbol, Warning(b))

    case tree @ Ident(_) =>
      val affectedSymbols =
        if (tree.symbol.owner.isConstructor) {
          val paramAccessors = tree.symbol.enclClass.constrParamAccessors
          paramAccessors.find(tree.symbol.name == _.name).toSeq :+ tree.symbol
        }
        else
          Seq(tree.symbol)
      affectedSymbols.foreach(ok(_))
  }
}

