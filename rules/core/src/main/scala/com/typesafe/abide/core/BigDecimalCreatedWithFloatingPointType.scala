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

  private lazy val JavaBigDecimal: ClassSymbol = rootMirror.getClassByName(TypeName("java.math.BigDecimal"))
  private lazy val ScalaBigDecimal: ClassSymbol = rootMirror.getClassByName(TypeName("scala.math.BigDecimal"))

  val step = optimize {

    // new java.math.BigDecimal(float|double)
    case appl @ q"new $who($what)" if isJavaBigDecimal(who.tpe) && (isFloat(what.tpe) || isDouble(what.tpe)) =>
      nok(Warning(appl))

    // new scala.BigDecimal(float)
    case appl @ q"new $who($what)" if isScalaBigDecimal(who.tpe) && isFloat(what.tpe) =>
      nok(Warning(appl))

    case appl @ q"scala.`package`.BigDecimal.apply($what)" if isFloat(what.tpe) =>
      nok(Warning(appl))

  }

  private def isFloat(t: Type): Boolean = t <:< typeOf[Float]
  private def isDouble(t: Type): Boolean = t <:< typeOf[Double]

  private def isJavaBigDecimal(t: Type): Boolean = t.baseType(JavaBigDecimal) ne NoType
  private def isScalaBigDecimal(t: Type): Boolean = t.baseType(ScalaBigDecimal) ne NoType

}
