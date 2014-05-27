package scala.tools.abide
package rules

import traversal._
import directives._
import reflect.runtime.universe._

class PublicMutable(val traverser : FastTraversal with MutabilityChecker) extends ExistentialRule {
  type Elem = traverser.global.Symbol
  import traverser._
  import global._

  val name = "public-mutable"

  val step = optimize {
    case valDef @ q"$mods val $name : $tpt = $value" if !mods.isSynthetic =>
      val getter : Symbol = valDef.symbol.getter
      if (getter.isPublic && mutable(tpt.tpe)) nok(valDef.symbol) else maintain
  }
}
