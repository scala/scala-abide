package scala.tools.abide.traversal

import scala.tools.abide._
import scala.tools.abide.directives._
import scala.reflect.internal.traversal._
import scala.collection.{ mutable, immutable }
import scala.annotation.tailrec

import scala.reflect.internal._

/**
 * ScopingRule
 *
 * TraversalRule subtrait that provides scoping from [[ScopingRuleTraversal]] with simple warning accumulation
 * from [[WarningRuleTraversal]], meaning warnings are accumulated given scoping information and local context.
 *
 * @see [[ScopingRuleTraversal]] for scala-style scoping aspects
 * @see [[WarningRuleTraversal]] for warning accumulation
 */
trait ScopingRule extends ScopingRuleTraversal with WarningRuleTraversal {
  import context.universe._

  def emptyState = State(Nil)
  case class State(warnings: List[Warning]) extends WarningState {
    def nok(warning: Warning): State = State(warning :: warnings)
  }
}

/**
 * ScopingRuleTraversal
 *
 * [[ContextGenerator]] subtype associated to [[ScopingRuleTraversal]] class that will automatically
 * extend the context with the [[scala.tools.abide.directives.ScopeProvider]] directive for subtypes
 * of `ScopingRuleTraversal`.
 *
 * @see [[ScopingRuleTraversal]]
 */
object ScopingRuleTraversal extends ContextGenerator[Context with ScopeProvider] {
  def getContext(universe: SymbolTable) = new Context(universe) with ScopeProvider
}

/**
 * ScopingRuleTraversal
 *
 * TraversalRule subtrait that provides scala-style symbol scoping and lets rule writers lookup symbols
 * given names and filters. To ensure the scoping mechanism resembles the one implemented in the scala
 * compiler, the scope building is handled automatically by the ScopingRuleTraversal trait.
 *
 * The context provides a `lookupContext(tree: Tree)` method that retrieves the typing context for a given
 * tree. This context of type `ScopingContext` can then be used to lookup symbols through
 * `lookupSymbol(name: Name, filter: Symbol => Boolean)`.
 *
 * The scoping mechanism is handled by the `ScopeProvider` directive that is mixed into the shared context.
 *
 * @see [[scala.tools.abide.directives.ScopeProvider]]
 */
trait ScopingRuleTraversal extends TraversalRule with ScopingTraversal {
  val context: Context with ScopeProvider
  import context.universe._
}

