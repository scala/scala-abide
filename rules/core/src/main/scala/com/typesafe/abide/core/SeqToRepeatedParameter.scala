package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

/**
 * Signal when a sequence is passed to a repeated parameter. Due to type inference, and especially
 *  with collection classes, this may lead to confusing results.
 *  {{{
 *      val buf = ListBuffer(1, 2)
 *
 *      Vector(buf) // Vector[ListBuffer[Int]], instead of Vector[Int]
 *  }}}
 *
 *  @note To minimize false positives, this rules fires only when the repeated parameter is a type parameter.
 */
class SeqToRepeatedParameter(val context: Context) extends ExistentialRule {
  import context.universe._

  val name = "Seq-in-repeated-parameter-position"
  type Key = Tree

  case class Warning(tree: Tree, argTpe: Type, elementTpe: Type) extends RuleWarning {
    val pos = tree.pos
    val message = s"The argument of type $argTpe is interpreted as a single repeated argument. To pass its elements as repeated arguments of type $elementTpe, append : _*"
  }

  import definitions._

  val step = optimize {
    case Apply(fun, args) =>
      for {
        sym <- Option(fun.symbol) if sym ne NoSymbol
        paramList <- fun.symbol.info.paramTypes.zip(args)
        (tp, arg) = paramList
        elementTpe = elementType(definitions.SeqClass, arg.tpe)
        if (isRepeatedParamType(tp) && (elementTpe ne NoType))
      } {
        val repeatedElementType = repeatedToSingle(tp)
        if ((repeatedElementType ne NoType) && repeatedElementType.typeSymbol.isAbstractType)
          nok(arg, Warning(arg, arg.tpe, elementTpe))
      }
  }
}
