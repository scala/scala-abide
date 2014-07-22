package com.typesafe.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class MatchCaseOnSeq(val context : Context) extends WarningRule {
  import context.universe._

  val name = "match-case-on-seq"

  case class Warning(scrut : Tree, mtch : Tree) extends RuleWarning {
    val pos = mtch.pos
    val message = s"Seq typed scrutinee $scrut shouldn't be matched against :: typed case $mtch"
  }

  lazy val seqSymbol = rootMirror.getClassByName(TypeName("scala.collection.Seq"))
  lazy val immutableSeqSymbol = rootMirror.getClassByName(TypeName("scala.collection.immutable.Seq"))
  def isSeq(sym : Symbol) : Boolean = sym == seqSymbol || sym == immutableSeqSymbol

  lazy val consSymbol = rootMirror.getClassByName(TypeName("scala.collection.immutable.$colon$colon"))
  def isCons(sym : Symbol) : Boolean = sym == consSymbol

  val step = optimize {
    case q"$scrut match { case ..$cases }" if scrut.tpe != null && isSeq(scrut.tpe.typeSymbol) =>
      val patterns = cases.collect {
        case cq"$bind @ $pat if $guard => $expr" => pat
        case cq"$pat if $guard => $expr" => pat
      }

      patterns.foreach(pat => pat match {
        case q"$id(..$args)" if id.tpe != null && isCons(id.tpe.resultType.typeSymbol) =>
          nok(Warning(scrut, pat))
        case _ =>
      })
  }
}
