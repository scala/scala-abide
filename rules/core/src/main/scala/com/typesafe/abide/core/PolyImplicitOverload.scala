package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class PolyImplicitOverload(val context: Context) extends WarningRule {
  import context.universe._

  val name = "poly-implicit-overload"

  case class Warning(val pos: Position) extends RuleWarning {
    val message: String =
      "Parametrized overloaded implicit methods are not visible as view bounds"
  }

  val step = optimize {
    case t @ Template(parents, self, body) =>
      val clazz = t.tpe
      clazz.declarations filter (x => x.isImplicit && x.typeParams.nonEmpty) foreach { sym =>
        // implicit classes leave both a module symbol and a method symbol as residue
        val alts = clazz.declaration(sym.name).alternatives filterNot (_.isModule)
        if (alts.size > 1) {
          nok(Warning(sym.pos))
        }
      }
  }
}
