package scala.tools.abide
package traversal

import directives._
import reflect.runtime.universe._

class PublicMutable(val analyzer : TraversalAnalyzer with MutabilityChecker) extends WarningRule {
  import analyzer._
  import global._

  val name = "public-mutable-fields"

  case class Warning(tree : Tree) extends RuleWarning {
    val pos = tree.pos
    val message = {
      val name = tree.asInstanceOf[ValDef].name
      s"Mutability should be encapsulated but mutable value $name is part of the public API"
    }
  }

  val step = optimize {
    case valDef @ q"$mods val $name : $tpt = $value" if !mods.isSynthetic && tpt.tpe != null =>
      val getter : Symbol = valDef.symbol.getter
      val owner : Symbol = valDef.symbol.owner
      if (getter.isPublic && (owner.isClass || owner.isModule) && mutable(tpt.tpe)) nok(Warning(valDef)) else maintain

    case varDef @ q"$mods var $name : $tpt = $value" if !mods.isSynthetic =>
      val getter : Symbol = varDef.symbol.getter
      val owner : Symbol = varDef.symbol.owner
      if (getter.isPublic && (owner.isClass || owner.isModule)) nok(Warning(varDef)) else maintain
  }
}
