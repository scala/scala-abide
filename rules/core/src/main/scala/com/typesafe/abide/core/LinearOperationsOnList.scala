package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._
import scala.collection.immutable.Vector

/**
 * Signal usages of `List` operations that have a linear cost (either in space or time).
 */
class LinearOperationsOnList(val context: Context) extends WarningRule {

  import context.universe._

  val name = "linear-cost-operations-on-list"

  case class Warning(pos: Position, message: String) extends RuleWarning

  private lazy val listType: Type = typeOf[List[_]]
  private lazy val vectorType: Type = typeOf[Vector[_]]

  private lazy val linearCostMethods: Set[Name] = Set(":+", "length", "lengthCompare", "size", "last", "lastOption", "init", "apply", "updated").map(TermName.apply(_).encode)

  val step: PartialFunction[Tree, Unit] = optimize {
    case t @ Select(qual, _) =>
      if (linearCostMethods.contains(t.symbol.name) && qual.tpe <:< listType) {
        val message = s"""
                        | You are calling method $name on a ${listType.getClass.getName},
                        | and this operation has a linear cost. Prefer using a ${vectorType.getClass.getName} 
                        | which takes effectively constant time for the same operation.
                       """.stripMargin
        nok(Warning(t.pos, message))
      }
  }
}