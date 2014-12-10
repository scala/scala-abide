package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class BigDecimalCreatedWithFloatingPointType(val context: Context) extends WarningRule {
  import context.universe._

  val name = "big-decimal-with-floating-point-type"

  case class Warning(t: Tree) extends RuleWarning {
    val pos = t.pos
    val message = "The results of using the BigDecimal constructor with floating point values is unpredictable"
  }

  def isByName(param: Symbol) = param.tpe.typeSymbol == definitions.ByNameParamClass

  val step = optimize {
    // BigDecimal(float|double)
    case appl @ Apply(Select(who, t), List(what)) if isBigDecimal(who) && isFloatingPoint(what) =>
      nok(Warning(appl))

    // new BigDecimal(float|double)
    case appl @ Apply(Select(New(who), nme.CONSTRUCTOR), List(what)) if isBigDecimal(who) && isFloatingPoint(what) =>
      nok(Warning(appl))
  }

  // checks if it is a scala big decima or a java big decimal
  private def isBigDecimal(t: Tree): Boolean =
    t.toString == "scala.`package`.BigDecimal" || t.toString == "java.math.BigDecimal"

  private def isFloatingPoint(t: Tree): Boolean = t.tpe <:< typeOf[Float] || t.tpe <:< typeOf[Double]

}
