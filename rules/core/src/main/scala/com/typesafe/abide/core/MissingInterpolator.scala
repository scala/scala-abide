package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class MissingInterpolator(val context: Context) extends ScopingRule {
  import context.universe._

  val name = "missing-interpolator"

  case class Warning(literal: Literal, name: Option[Name]) extends RuleWarning {
    val pos: Position = literal.pos
    val message: String = if (name.nonEmpty) {
      s"Possible missing interpolator: detected an interpolated identifier `$$${name.get}`"
    }
    else {
      s"Possible missing interpolator: detected an interpolated expression"
    }
  }

  // PlausibleNames are names and booleans representing whether they can be
  // used in interpolations. Implausible is used for shadowing previously
  // plausible names.
  abstract class PlausibleName(val name: Name, val isPlausible: Boolean)
  case class Plausible(override val name: Name) extends PlausibleName(name, true)
  case class Implausible(override val name: Name) extends PlausibleName(name, false)

  // State is a pair representing a name in scope along with a boolean
  // The boolean is true if literals in the current scope are recognizably not
  // for interpolation (e.g. in implicitNotFound annotations)
  type Owner = (Boolean, PlausibleName)

  val InterpolatorCodeRegex = """\$\{.*?\}""".r
  val InterpolatorIdentRegex = """\$[$\w]+""".r // note that \w doesn't include $

  def requiresNoArgs(tp: Type): Boolean = tp match {
    case PolyType(_, restpe)     => requiresNoArgs(restpe)
    case MethodType(Nil, restpe) => requiresNoArgs(restpe) // May be a curried method - can't tell yet
    case MethodType(p :: _, _)   => p.isImplicit // Implicit method requires no args
    case _                       => true // Catches all others including NullaryMethodType
  }

  def isRecognizablyNotForInterpolation: Boolean = state.scope.headOption.map(_._1).getOrElse(false)
  def isPlausible(m: Name) = state.scope.map(_._2).find(sn => sn.name == m).map(_.isPlausible).getOrElse(false)

  def enterPlausible(symbols: Iterable[Symbol]) = {
    if (!isRecognizablyNotForInterpolation) {
      for (s <- symbols if s.alternatives.exists(sa => requiresNoArgs(sa.typeSignature)) && !isPlausible(s.name)) {
        enter((false, Plausible(s.name)))
      }
    }
  }
  def enterImplausible(symbols: Iterable[Symbol]) = {
    if (!isRecognizablyNotForInterpolation) {
      for (s <- symbols if !requiresNoArgs(s.typeSignature) && isPlausible(s.name)) {
        enter(false, (Implausible(s.name)))
      }
    }
  }

  def enterNotForInterpolation() = enter((true, state.scope.head._2))

  val step = optimize {
    case Apply(Select(Apply(RefTree(_, nme.StringContext), _), _), _) =>
      enterNotForInterpolation()

    case Apply(Select(New(RefTree(_, tpnme.implicitNotFound)), _), _) =>
      enterNotForInterpolation()

    case t @ Template(parents, self, body) =>
      // Add all inherited members to scope
      enterPlausible(t.tpe.members.sorted diff t.tpe.declarations.sorted)
      // Also consider private declarations in the parent class to be plausible
      enterPlausible(parents.flatMap(p => p.symbol.tpe.declarations).filter(_.isPrivate))
      // Invalidate plausible names which are shadowed by implausible ones
      enterImplausible(t.tpe.declarations)
      // Add this template's declarations as plausible
      enterPlausible(t.tpe.declarations)

    case d @ DefDef(_, _, _, vparamss, _, _) =>
      enterPlausible(vparamss.flatten.map(_.symbol))

    case p @ PackageDef(pid, stats) =>
      enterImplausible(pid.tpe.members)
      enterPlausible(pid.tpe.members)

    case Block(stats, expr) =>
      def isDeclaration(tree: Tree) = tree match {
        case ValDef(_, _, _, _) | DefDef(_, _, _, _, _, _) => true
        case _ => false
      }
      val decls = stats.filter(isDeclaration(_)).map(_.symbol)
      // This is imprecise, it acts as if all of a blocks declarations occur at
      // the beginning of the block
      enterPlausible(decls)
      enterImplausible(decls)

    case lit @ Literal(Constant(s: String)) if !isRecognizablyNotForInterpolation =>
      def suspiciousExpr = InterpolatorCodeRegex findFirstIn s
      def suspiciousIdents = InterpolatorIdentRegex findAllIn s map (id => newTermName(id drop 1))

      // No warning on e.g. a string with only "$ident"
      if (s contains ' ') {
        if (suspiciousExpr.nonEmpty) nok(Warning(lit, None))
        else suspiciousIdents find isPlausible foreach (sym => {
          nok(Warning(lit, Some(sym)))
        })
      }
  }
}
