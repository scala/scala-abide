package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class DelayedInitSelect(val context: Context) extends WarningRule {
  import context.universe._

  val name = "delayed-init-select"

  case class Warning(symbol: Symbol) extends RuleWarning {
    val pos = symbol.pos
    val message =
      s"Selecting $symbol from ${symbol.owner} which extends scala.DelayedInit is likely to yield an uninitialized value"
  }

  val step = optimize {
    case sel @ Select(qual, _) =>
      val symbol = sel.symbol
      val isLikelyUninitialized =
        (symbol.owner.tpe <:< typeOf[scala.DelayedInit]) &&
          !qual.tpe.isInstanceOf[ThisType] &&
          symbol.accessedOrSelf.isVal

      if (isLikelyUninitialized) {
        nok(Warning(symbol))
      }

  }
}
