package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._
import scala.tools.abide.directives._

class TypeParameterShadow(val context: Context with ScopeProvider) extends ScopingRule {
  import context._
  import context.universe._

  val name = "type-parameter-shadow"

  case class Warning(tp: TypeDef, sym1: Symbol, sym2: Symbol) extends RuleWarning {
    val pos: Position = tp.pos
    val message: String = s"Type parameter ${tp.name} defined in $sym1 shadows $sym2 defined in ${sym2.owner}. You may want to rename your type parameter, or possibly remove it."
  }

  def warnTypeParameterShadow(tree: Tree, tparams: List[TypeDef]) = {
    val sym = tree.symbol

    if (!sym.isSynthetic) {
      def enclClassOrMethodOrTypeMember(c: ScopingContext): ScopingContext =
        if (!c.owner.exists || c.owner.isClass || c.owner.isMethod || (c.owner.isType && !c.owner.isParameter)) c
        else enclClassOrMethodOrTypeMember(c.outer)

      val ctx = context.lookupContext(tree)
      tparams.filter(_.name != typeNames.WILDCARD).foreach { tp =>
        // we don't care about type params shadowing other type params in the same declaration
        enclClassOrMethodOrTypeMember(ctx).outer.lookupSymbol(tp.name, s => s != tp.symbol && s.hasRawInfo && s.exists) match {
          case LookupSucceeded(_, sym2) =>
            nok(Warning(tp, sym, sym2))
          case _ =>
        }
      }
    }
  }

  val step = optimize {
    case cd @ ClassDef(_, _, tparams, _) =>
      warnTypeParameterShadow(cd, tparams)
    case dd @ DefDef(_, _, tparams, _, _, _) =>
      warnTypeParameterShadow(dd, tparams)
    case td @ TypeDef(_, _, tparams, _) =>
      warnTypeParameterShadow(td, tparams)
  }
}
