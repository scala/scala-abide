package scala.tools.abide
package traversal

import reflect.runtime.universe._

class MatchCaseOnSeq(val analyzer : TraversalAnalyzer) extends WarningRule {
  import analyzer.global._

  val name = "match-case-on-seq"

  case class Warning(scrut : Tree, mtch : Tree) extends RuleWarning {
    val pos = mtch.pos
    val message = s"Seq typed scrutinee $scrut cannot match against :: or Nil typed case $mtch"
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

      patterns.foldLeft(maintain)((state, pat) => pat match {
        case q"$id(..$args)" if id.tpe != null &&
          id.tpe.resultType.typeSymbol == typeOf[scala.collection.immutable.::[Any]].typeSymbol =>
            state and nok(Warning(scrut, pat))
        case q"$nil" if nil.tpe != null &&
          nil.tpe.typeSymbol == typeOf[Nil.type].typeSymbol =>
            state and nok(Warning(scrut, pat))
        case _ => state
      })
  }
}
