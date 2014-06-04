package scala.tools.abide
package traversal

import directives._
import reflect.runtime.universe._

class PublicMutable(val analyzer : TraversalAnalyzer with MutabilityChecker) extends WarningRule {
  import analyzer._
  import global._

  val name = "public-mutable-fields"

  val step = optimize {
    case valDef @ q"$mods val $name : $tpt = $value" if !mods.isSynthetic =>
      val getter : Symbol = valDef.symbol.getter
      if (getter.isPublic && mutable(tpt.tpe)) nok(valDef.pos) else maintain

    case varDef @ q"$mods var $name : $tpt = $value" if !mods.isSynthetic =>
      val getter : Symbol = varDef.symbol.getter
      if (getter.isPublic) nok(varDef.pos) else maintain
  }
}
