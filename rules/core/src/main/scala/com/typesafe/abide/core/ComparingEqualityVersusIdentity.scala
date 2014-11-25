package com.typesafe.abide.core

import scala.tools.abide.Context
import scala.tools.abide.traversal.WarningRule

/**
 * Signal usages of `equals` or `==` on reference types that do not provide a custom implementation
 * of `scala.Any.equals`.
 *
 * The rational is that if a reference type does not provide an implementation of `scala.Any.equals`,
 * then we are effectively comparing object's identities, and hence `eq` or `neq` should be used instead,
 * as it reveals the intention.
 */
class ComparingEqualityVersusIdentity(val context: Context) extends WarningRule {
  import context.universe._

  val name = "comparing-equality-versus-identity"

  private val equalsName = TermName("equals")
  private val eqName = TermName("eq")
  private val equalityNames: Set[Name] = Set(equalsName, TermName("=="))

  case class Warning(pos: Position, message: String) extends RuleWarning

  val step: PartialFunction[Tree, Unit] = {
    case t @ Select(qual, name) =>
      if (equalityNames.contains(name) &&
        !isModuleType(qual.tpe) && // If `qual`'s type is a module, then don't run the rule because calling `equals` on a singleton is OK
        isReferenceType(qual.tpe) && // This rule should be run only for reference types (and not for value types)  
        !hasEqualsMethod(qual.tpe)) {
        val message = s"""
                        | You are calling `$name` on type ${qual}, which does not provide an 
                        | implementation of `$equalsName`. Because you are effectively comparing 
                        | identities, prefer using `$eqName` instead.
                       """.stripMargin
        nok(Warning(t.pos, message))
      }
  }

  private def isModuleType(t: Type): Boolean = t.widen.typeSymbol.isModuleClass
  private def isReferenceType(t: Type): Boolean = t <:< typeOf[AnyRef]
  private def isAnyRefType(t: Type): Boolean = t =:= typeOf[AnyRef]

  /**
   * Returns true if the passed type `t` provides an implementation of `equals` with the expected
   * signature (as defined by `scala.Any.equals`).
   */
  @annotation.tailrec
  private def hasEqualsMethod(t: Type): Boolean = {
    val member = t.decl(equalsName)
    val equalsSymbol = if (member.isOverloaded) {
      val overloads = member.alternatives
      overloads.find(isEqualsMethodSignature).getOrElse(NoSymbol)
    }
    else member

    // going up in the inheritance chain to find an implementation of equals, but skipping `scala.AnyRef` and 
    // its parent (i.e., `scala.Any`) because we are not interested in the default implementation of `equals` 
    // provided by these classes.
    if (equalsSymbol == NoSymbol && !isAnyRefType(t.firstParent)) hasEqualsMethod(t.firstParent)
    else equalsSymbol != NoSymbol
  }

  /**
   * Returns true if the passed symbol `s` is a method symbol whose signature matches the definition of method
   * `equals` in `scala.Any`.
   */
  private def isEqualsMethodSignature(s: Symbol): Boolean = {
    assert(s.isMethod, s"Expected symbol method, found $s")
    assert(s.name == equalsName, s"Expected a symbol for method `$equalsName`, found `${s.name}`")

    val method = s.asMethod
    method.paramss match {
      case List(List(paramSym)) =>
        definitions.AnyTpe == paramSym.tpe && method.returnType == definitions.BooleanTpe
      case _ => false
    }
  }
}