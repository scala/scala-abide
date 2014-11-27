package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class Inaccessible(val context: Context) extends WarningRule {
  import context.universe._

  val name = "inaccessible"

  case class Warning(val pos: Position, methodName: Name, className: Name, inaccessibleClassName: Name) extends RuleWarning {
    val message =
      s"""Method $methodName in class $className references private class $inaccessibleClassName.
         |Classes which cannot access $inaccessibleClassName may be unable to override $methodName""".stripMargin
  }

  implicit class SymbolOps(sym: Symbol) {
    def isModuleOrModuleClass = sym.isModule || sym.isModuleClass

    def isTopLevel = sym.owner.isPackageClass

    def isLocalToBlock: Boolean = sym.owner.isTerm

    def isEffectivelyFinal: Boolean = (
      sym.isFinal
      || sym.hasPackageFlag
      || isModuleOrModuleClass && isTopLevel
      || sym.isTerm && (sym.isPrivate || isLocalToBlock)
    )

    def isNotOverridden = {
      if (sym.owner.isClass) {
        val classOwner = sym.owner.asClass
        if (classOwner.isSealed) {
          classOwner.knownDirectSubclasses.forall(c => {
            c.isEffectivelyFinal && c.typeSignature.declarations.exists(subSym => subSym.allOverriddenSymbols.contains(sym))
          })
        }
        else classOwner.isEffectivelyFinal
      }
      else false
    }

    def isDeferred = (sym.flags & Flag.DEFERRED) != 0

    def isEffectivelyFinalOrNotOverridden: Boolean = {
      isEffectivelyFinal || (sym.isTerm && !isDeferred && isNotOverridden)
    }

    def isLessAccessibleThan(other: Symbol): Boolean = {
      val tb = sym.accessBoundary(sym.owner)
      val ob1 = other.accessBoundary(sym.owner)
      val ob2 = ob1.linkedClassOfClass
      var o = tb
      while (o != NoSymbol && o != ob1 && o != ob2) {
        o = o.owner
      }
      o != NoSymbol && o != tb
    }

    def hasAccessBoundary = sym.privateWithin != NoSymbol

    def accessBoundary(base: Symbol): Symbol = {
      def enclosingRootClass(s: Symbol): Symbol = s match {
        case s: ClassSymbol => s
        case _              => enclosingRootClass(s.owner)
      }
      if (sym.isPrivate || sym.isLocalToBlock) sym.owner
      else if (sym.isProtected && sym.isStatic && sym.isJava) enclosingRootClass(sym)
      else if (hasAccessBoundary) sym.privateWithin
      else if (sym.isProtected) base
      else enclosingRootClass(sym)
    }
  }

  val step = optimize {
    case defDef @ DefDef(_, name, _, _, _, _) if !defDef.symbol.isSynthetic =>
      val member = defDef.symbol
      val isConstructor = member.isMethod && member.asMethod.isConstructor
      if (!isConstructor && !member.isEffectivelyFinalOrNotOverridden) {

        def checkAccessibilityOfType(tpe: Type) = {
          val inaccessible = lessAccessibleSymsInType(tpe, member)
          // If the unnormalized type is accessible, that's good enough
          if (inaccessible.isEmpty) ()
          // Or if the normalized type is, that's good too
          else if ((tpe ne tpe.normalize) && lessAccessibleSymsInType(tpe.dealiasWiden, member).isEmpty) ()
          // Otherwise warn about the inaccessible syms in the unnormalized type
          else inaccessible foreach (sym => {
            nok(Warning(defDef.pos, name, sym.owner.name, sym.name))
          })
        }

        // Types of the value parameters
        mapParamss(member)(p => checkAccessibilityOfType(p.tpe))
        // Upper bounds of type parameters
        member.typeParams.map(_.info.bounds.hi.widen) foreach checkAccessibilityOfType
      }

      def lessAccessibleSymsInType(other: Type, memberSym: Symbol): List[Symbol] = {
        val extras = other match {
          case TypeRef(pre, _, args) =>
            // Checking the prefix here gives us spurious errors on e.g. a
            // private[process] object which contains a type alias, which
            // normalizes to a visible type.
            args filterNot (_ eq NoPrefix) flatMap (tp => lessAccessibleSymsInType(tp, memberSym))
          case _ => Nil
        }
        if (lessAccessible(other.typeSymbol, memberSym)) other.typeSymbol :: extras
        else extras
      }

      def lessAccessible(otherSym: Symbol, memberSym: Symbol): Boolean = (
        (otherSym != NoSymbol)
        && !otherSym.isProtected
        && !otherSym.isTypeParameterOrSkolem
        && !otherSym.isExistentiallyBound
        && (otherSym isLessAccessibleThan memberSym)
        && (otherSym isLessAccessibleThan memberSym.enclClass)
      )
  }
}
