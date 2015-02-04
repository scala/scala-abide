package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class NullaryUnit(val context: Context) extends WarningRule with SimpleWarnings {
  import context.universe._

  val warning = w"Side-effecting nullary methods are discouraged: try defining as `def ${tree.symbol.name}()` instead"

  // Don't warn for e.g. the implementation of a generic method being parameterized on Unit
  def isOk(sym: Symbol) = (
    sym.isGetter
    || (sym.name containsName nme.DEFAULT_GETTER_STRING)
    || sym.allOverriddenSymbols.exists(over => !(over.tpe.resultType =:= sym.tpe.resultType))
  )

  def check(df: Tree) = df.symbol.tpe match {
    case NullaryMethodType(resttp) if resttp =:= typeOf[Unit] && !isOk(df.symbol) =>
      nok(df)
    case _ => ()
  }

  val step = optimize {
    case valDef @ ValDef(_, _, _, _)       => check(valDef)
    case defDef @ DefDef(_, _, _, _, _, _) => check(defDef)
  }
}
