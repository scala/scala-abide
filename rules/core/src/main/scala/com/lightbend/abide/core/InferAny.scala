package com.lightbend.abide.core

import scala.tools.abide._
import scala.tools.abide.traversal._

class InferAny(val context: Context) extends PathRule {
  import context.universe._

  val name = "infer-any"

  case class Warning(app: Tree, tpt: Tree) extends RuleWarning {
    val pos = app.pos
    val message = s"A type was inferred to be `${tpt.tpe.typeSymbol.name}`. This may indicate a programming error."
  }

  // Use PathRule to track whether the traversal is currently inside a synthetic method
  // (for example a method generated for a case class) which should be ignored.
  type Element = Unit
  def inSyntheticMethod = state.last.nonEmpty
  def enterSyntheticMethod() = enter(())

  def containsAny(t: Type) =
    t.contains(typeOf[Any].typeSymbol) || t.contains(typeOf[AnyVal].typeSymbol)

  def isInferredAny(tree: Tree) = tree match {
    case tpt @ TypeTree() => tpt.original == null && containsAny(tpt.tpe)
    case _                => false
  }

  val step = optimize {
    case df @ DefDef(_, _, _, _, _, _) if df.symbol.isSynthetic =>
      enterSyntheticMethod()

    case app @ Apply(TypeApply(fun, targs), args) if targs.exists(isInferredAny) && !inSyntheticMethod =>
      val existsExplicitAny = args.map(_.tpe).exists(containsAny(_))
      if (!existsExplicitAny) {
        nok(Warning(app, targs.find(isInferredAny).get))
      }
  }

}
