package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class TypeParameterShadow(val context: Context) extends ScopingRule {
  import context.universe._

  val name = "type-parameter-shadow"

  case class Warning(tp: TypeDef, sym1: Symbol, sym2: Option[Symbol]) extends RuleWarning {
    val pos: Position = tp.pos
    val message: String =
      if (sym2.nonEmpty) s"Type parameter ${tp.name} defined in $sym1 shadows ${sym2.get} defined in ${sym2.get.owner}. You may want to rename your type parameter, or possibly remove it."
      else s"Type parameter ${tp.name} defined in $sym1 shadows predefined type ${tp.name}"

  }

  // State is a pair of List[Symbol] (the declared type parameters) and Boolean
  // (true if the declarer is a class or method or type member)
  type Owner = Symbol

  // Any of the types in scala.Predef could be shadowed
  val predefTypes = List("List", "Double", "Float", "Long", "Int", "Char", "Short", "Byte", "Unit", "Boolean") ++ scala.reflect.runtime.universe.typeOf[scala.Predef.type].decls.filter(_.isType).map(_.name.toString)

  def getDeclaration(tp: TypeDef, scope: List[Owner]) = scope.find { sym =>
    sym.name == tp.name && sym.isType
  }

  def enclClassOrMethodOrTypeMemberScope: List[Owner] = state.scope.dropWhile { sym =>
    !sym.isClass && !sym.isMethod && !(sym.isType && !sym.isParameter)
  }

  def warnTypeParameterShadow(tparams: List[TypeDef], sym: Symbol) = {
    if (!sym.isSynthetic) {
      val tt = tparams.filter(_.name != typeNames.WILDCARD).foreach { tp =>
        if (predefTypes.contains(tp.name.toString)) nok(Warning(tp, sym, None))
        // We don't care about type params shadowing other type params in the same declaration
        else {
          getDeclaration(tp, enclClassOrMethodOrTypeMemberScope) foreach { prevTp =>
            if (prevTp != tp.symbol) {
              nok(Warning(tp, sym, Some(prevTp)))
            }
          }
        }

      }
    }
  }

  def enterAll(possTypes: List[Symbol]) = possTypes.map(enter(_))

  val step = optimize {
    case t @ Template(parents, self, body) =>
      enterAll(t.tpe.members.toList)
    case p @ PackageDef(pid, stats) =>
      enterAll(pid.tpe.members.toList)

    case cd @ ClassDef(_, _, tparams, _) =>
      enter(cd.symbol)
      warnTypeParameterShadow(tparams, cd.symbol)
      enterAll(tparams.map(_.symbol))
    case dd @ DefDef(_, _, tparams, _, _, _) =>
      enter(dd.symbol)
      warnTypeParameterShadow(tparams, dd.symbol)
      enterAll(tparams.map(_.symbol))
    case td @ TypeDef(_, _, tparams, _) =>
      enter(td.symbol)
      warnTypeParameterShadow(tparams, td.symbol)
      enterAll(tparams.map(_.symbol))
  }
}
