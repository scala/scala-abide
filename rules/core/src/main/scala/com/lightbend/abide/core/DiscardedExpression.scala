package com.lightbend.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class DiscardedExpression(val context: Context) extends ExistentialRule {
  import context.universe._

  val name = "discarded-expression"
  type Key = Symbol

  case class Warning(tree: Tree) extends RuleWarning {
    val pos = tree.pos
    val message = s"${tree.symbol} is discarded."
  }

  private val WarnPrefixes = List("Int", "foo.")

  val step = optimize {
    case Block(stats, _) =>
      val discarded = stats.filter { s =>
        val typeAsString = s.tpe.dealias.toString()
        WarnPrefixes.exists(p => typeAsString.startsWith(p))
      }

      discarded.foreach { exp =>
        nok(exp.symbol, Warning(exp))
      }
  }
}

