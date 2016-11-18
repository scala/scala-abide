package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

final class ValueClassSynchronized(val context: Context) extends PathRule {
  import context.universe._
  import definitions.{Object_synchronized, PredefModule}

  val name = "value-class-synchronized"
  type Element = Symbol

  case class Warning(ap: Apply) extends RuleWarning {
    val pos = ap.pos
    val message = "Within a user defined value class, `synchronized {}` locks `Predef`, which is unwise and probably unexpected"
  }

  private def enclosingClass = state.last.getOrElse(NoSymbol)

  val step = optimize {
    case tmpl: ImplDef =>
      enter(tmpl.symbol)
    case ap @ Apply(TypeApply(sel @ Select(qual, _), _), _) =>
      if (enclosingClass.isDerivedValueClass && sel.symbol == Object_synchronized && qual.symbol == PredefModule)
        nok(Warning(ap))
  }
}
