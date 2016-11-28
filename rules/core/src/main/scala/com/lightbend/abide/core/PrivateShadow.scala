package com.lightbend.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

// https://issues.scala-lang.org/browse/SI-4762
class PrivateShadow(val context: Context) extends WarningRule {
  import context.universe._

  val name = "private-shadow"

  case class Warning(val pos: Position, sym: Symbol, m2: Symbol) extends RuleWarning {
    val message: String = s"${sym.accessString} ${sym.fullLocationString} shadows mutable $m2 inherited from ${m2.owner}. Changes to $m2 will not be visible within ${sym.owner} - you may want to give them distinct names."
  }

  val step = optimize {
    case sel @ Select(qual @ This(_), _) =>
      val sym = sel.symbol
      if (sym.isPrivateThis &&
        (!sym.isMethod || sym.asMethod.paramss.isEmpty) &&
        qual.symbol.isClass) {

        qual.symbol.asClass.baseClasses.drop(1) foreach { parent =>
          parent.typeSignature.declarations.filterNot(x => x.isPrivate || x.isLocalToThis) foreach { m2 =>
            if (sym.name == m2.name &&
              m2.isMethod &&
              m2.asMethod.isGetter &&
              m2.asMethod.accessed.isVar) {

              nok(Warning(sel.pos, sym, m2))
            }
          }
        }
      }
  }
}
