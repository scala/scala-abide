package com.typesafe.abide.sample

import scala.tools.abide._
import scala.tools.abide.traversal._

class UnusedMember(val context : Context) extends ExistentialRule {
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

    val ignore = ((isValueParameter && owner.isDeferred) // abstract method, params are never used
      || owner.isConstructor // right after typer constructors are empty, so their params are not used
      || sym.isSynthetic
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
    case tree @ q"$mods val $name: $tpt = $rhs" if shouldConsider(tree.symbol) =>
      nok(tree.symbol, Warning(tree))

    case tree @ q"$pre.$name" =>
      ok(tree.symbol)

    case tree @ Ident(_) =>
      val affectedSymbols =
        if (tree.symbol.owner.isConstructor) {
          val paramAccessors = tree.symbol.enclClass.constrParamAccessors
          paramAccessors.find(tree.symbol.name == _.name).toSeq :+ tree.symbol
        } else
          Seq(tree.symbol)
      affectedSymbols.foreach(ok(_))
  }
}

