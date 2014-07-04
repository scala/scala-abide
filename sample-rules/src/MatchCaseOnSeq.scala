package com.typesafe.abide.sample

import scala.tools.abide._
import scala.tools.abide.traversal._

class MatchCaseOnSeq(val context : Context) extends WarningRule {
  import context.universe._

  val name = "match-case-on-seq"

  case class Warning(scrut : Tree, mtch : Tree) extends RuleWarning {
    val pos = mtch.pos
    val message = s"Seq typed scrutinee $scrut cannot match against :: typed case $mtch"
  }

  val step = optimize {
    case q"$scrut match { case ..$cases }" if scrut.tpe != null && (
      scrut.tpe.typeSymbol == typeOf[scala.collection.Seq[Any]].typeSymbol ||
      scrut.tpe.typeSymbol == typeOf[scala.collection.immutable.Seq[Any]].typeSymbol
    ) =>
      val patterns = cases.collect {
        case cq"$bind @ $pat if $guard => $expr" => pat
        case cq"$pat if $guard => $expr" => pat
      }

      patterns.foreach(pat => pat match {
        case q"$id(..$args)" if id.tpe != null &&
          id.tpe.resultType.typeSymbol == typeOf[scala.collection.immutable.::[Any]].typeSymbol =>
            nok(Warning(scrut, pat))
        case _ =>
      })
  }
}
