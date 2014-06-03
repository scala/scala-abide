package scala.tools.abide
package rules

import reflect.runtime.universe._

class MatchCaseOnSeq(val analyzer : Analyzer) extends ExistentialRule {
  type Elem = analyzer.global.Position
  import analyzer._
  import global._

  val name = "match-case-on-seq"

  lazy val collectionSeq = rootMirror.getClassByName(TypeName("scala.collection.Seq"))
  lazy val immutableSeq = rootMirror.getClassByName(TypeName("scala.collection.immutable.Seq"))
  def isSeq(tpe : Type) : Boolean = tpe.typeSymbol == collectionSeq || tpe.typeSymbol == immutableSeq

  lazy val listCons = rootMirror.getClassByName(TypeName("scala.collection.immutable.$colon$colon"))
  def isListCons(tpe : Type) : Boolean = tpe.resultType.typeSymbol == listCons

  lazy val listNil = rootMirror.getModuleByName(TermName("scala.collection.immutable.Nil")).moduleClass
  def isListNil(tpe : Type) : Boolean = tpe.typeSymbol == listNil

  val step = optimize {
    case q"$scrut match { case ..$cases }" if isSeq(scrut.tpe) =>
      val patterns = cases.collect {
        case cq"$bind @ $pat if $guard => $expr" => pat
        case cq"$pat if $guard => $expr" => pat
      }

      patterns.foldLeft(maintain)((state, pat) => pat match {
        case q"$id(..$args)" if id.tpe != null && isListCons(id.tpe) => state and nok(pat.pos)
        case q"$nil" if nil.tpe != null && isListNil(nil.tpe) => state and nok(pat.pos)
        case _ => state
      })
    case q"$scrut match { case ..$cases }" =>
      maintain
  }
}
