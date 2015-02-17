package scala.tools.abide.directives

import scala.tools.abide._
import scala.reflect.internal._
import scala.tools.nsc.Global
import scala.annotation._

/**
 * ScopeProvider
 *
 * Directive that enables context accumulation in the scala compiler and uses this information to provide
 * a scoping context lookup API given a current tree. Context lookup is provided through the [[lookupContext]]
 * method that retrieves the context associated to a given `tree`.
 *
 * This directive is useful for rules that need to access scoping information given `String` symbol names
 * that haven't been typechecked and bound to symbols by the typer run.
 */
trait ScopeProvider {
  val universe: SymbolTable
  import universe._

  /** We need a `scala.tools.nsc.Global` stable object here for path dependent `Context` types */
  private[ScopeProvider] val global: Global = universe.asInstanceOf[Global]

  /**
   * ScopingContext
   *
   * Provides a wrapper to the typing context used in the Scala compiler. The typing context has a tree-like
   * structure so the parent context can be accessed through [[outer]]. The currently owning symbol can be
   * found in [[owner]] and most importantly, one can perform named symbol lookup through [[lookupSymbol]].
   */
  class ScopingContext(
      private[ScopeProvider] val context: global.analyzer.Context,
      _outer: ScopingContext
  ) {
    /** Parent context */
    lazy val outer: ScopingContext = if (_outer == null) NoScopingContext else _outer

    /** Containing symbol (e.g. class, method, val, etc.) */
    lazy val owner: universe.Symbol = context.owner.asInstanceOf[universe.Symbol]

    /** Symbol lookup based on searched `name` and filter `suchThat` */
    def lookupSymbol(name: universe.Name, suchThat: universe.Symbol => Boolean): universe.NameLookup = {
      context.lookupSymbol(name.asInstanceOf[global.Name], suchThat.asInstanceOf[global.Symbol => Boolean]).asInstanceOf[universe.NameLookup]
    }
  }

  // map from scala compiler contexts to our contexts that is used to build context tree in `ScopingContext`
  private val contexts = scala.collection.mutable.Map.empty[global.analyzer.Context, ScopingContext]

  // ScopingContext wrapper for global.analyzer.NoContext
  private object NoScopingContext extends ScopingContext(global.analyzer.NoContext, null)

  /** Recursively transform global.analyzer.Context context tree into ScopingContext tree */
  private def getContext(context: global.analyzer.Context): ScopingContext = {
    if (context == global.analyzer.NoContext) NoScopingContext
    else contexts.get(context) match {
      case Some(scopingContext) => scopingContext
      case None =>
        val outerContext = getContext(context.outer)
        val scopingContext = new ScopingContext(context, outerContext)
        contexts(context) = scopingContext
        scopingContext
    }
  }

  /**
   * AnalyzerPlugin injected into the compiler that gets `pluginsTyped` called on typed tree. We use
   * this to extract typing contexts and wrap them into ScopingContext instances that can be presented
   * to rule writers.
   *
   * Unfortunately, `pluginsTyped` doesn't get called on all ASTs during typing, so holes need to be
   * patched by walking up the context chain and associated contexts to trees for which this hasn't
   * been done yet.
   */
  private class ScopingPlugin extends global.analyzer.AnalyzerPlugin {
    import global._
    import global.analyzer._

    override def pluginsTyped(tpe: Type, typer: Typer, tree: Tree, mode: Mode, pt: Type): Type = {
      val context = getContext(typer.context)

      var ctx: ScopingContext = context
      var lastCtx: ScopingContext = null
      while (ctx != NoScopingContext && ctx != lastCtx && !ctx.context.tree.hasAttachment[ScopingContext]) {
        ctx.context.tree.updateAttachment(ctx)

        lastCtx = ctx
        ctx = context.outer
      }
      tpe
    }
  }

  global.analyzer.addAnalyzerPlugin(new ScopingPlugin)

  /** Retrieve `ScopingContext` based on current `tree`. */
  def lookupContext(tree: Tree): ScopingContext = {
    tree.attachments.get[ScopingContext].getOrElse(NoScopingContext)
  }
}

