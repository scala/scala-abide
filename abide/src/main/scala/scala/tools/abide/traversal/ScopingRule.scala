package scala.tools.abide.traversal

import scala.reflect.internal.traversal._

/**
 * ScopingRule
 *
 * TraversalRule subtrait that provides helper methods to manage symbol scoping during traversal. The [[enterScope]] method
 * will open a new scoping block and [[scope]] will push the current symbol to the scope generated in [[enterScope]].
 * Once the traversal leaves the current scoping block, it is popped to ensure scoping equivalence with scala.
 *
 * As in [[WarningRule]], warnings are determined given local (and scoping) context in a single pass (no
 * validation/invalidation mechanism).
 */
trait ScopingRule extends TraversalRule with ScopingTraversal {
  import context.universe._

  def emptyState = State(List(Nil), Nil)
  case class State(scope: List[List[Symbol]], warnings: List[Warning]) extends RuleState {
    def enterScope: State = State(Nil :: scope, warnings)
    def scope(elem: Symbol): State = State((elem :: scope.head) :: scope.tail, warnings)
    def nok(warning: Warning): State = State(scope, warning :: warnings)

    private[ScopingRule] def leaveScope: State = State(scope.tail, warnings)

    def lookup(matcher: Symbol => Boolean): Option[Symbol] = scope.flatMap(xs => xs find matcher).headOption
  }

  override lazy val computedStep = {
    val scopingStep = optimize {
      case Block(decls, result) =>
        enterScope()

      case vd: ValDef =>
        scope(vd.symbol.name, vd.symbol)

      case dd: DefDef =>
        scope(dd.symbol.name, dd.symbol)
        enterScope()
        for (vparams <- dd.vparamss; vparam <- vparams) {
          scope(vparam.symbol.name, vparam.symbol)
        }

      case i @ Import(expr, selectors) =>
        val tpe = i.symbol.info match {
          case ImportType(expr) => expr.tpe
          case _                => NoType
        }

        // @see [[scala.tools.nsc.typechecker.ImportInfo.transformImport]]
        def transformImport(selectors: List[ImportSelector], sym: Symbol): List[Symbol] = selectors match {
          case Nil => Nil
          case ImportSelector(nme.WILDCARD, _, _, _) :: Nil => List(sym)
          case ImportSelector(from, _, to, _) :: _ if from == sym.name =>
            if (to == nme.WILDCARD) Nil else List(sym.cloneSymbol(sym.owner, sym.rawflags, to))
          case _ :: res => transformImport(res, sym)
        }

        val imported = importableMembers(tpe) flatMap (transformImport(selectors, _))
        println(imported)

        println(i.symbol, i.symbol.getClass, i.symbol.toType.members)
        for (member <- expr.tpe.members; selector <- selectors) {
          if (selector.name == nme.WILDCARD) {
            scope(member.name, member)
          }
          else if (selector.name == member.name) {
            scope(selector.name, member)
          }
        }

    }

    step merge scopingStep
  }

  /** Open new scoping context (typically will happen when encountering a Block tree */
  def enterScope(): Unit = { transform(_.enterScope, _.leaveScope) }

  /** Add an element to the current scope (will be popped when leaving scope opening point */
  def scope(name: Name, elem: Symbol): Unit = { transform(_ scope elem) }

  /** Reports a warning */
  def nok(warning: Warning): Unit = { transform(_ nok warning) }
}
