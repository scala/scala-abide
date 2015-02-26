package scala.tools.abide.directives

import scala.tools.abide._
import scala.reflect.internal._
import scala.tools.nsc.Global
import scala.annotation._

import scala.reflect.internal.util.{ SourceFile, NoSourceFile }

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.{ Map => MutableMap }

/**
 * ScopeProvider
 *
 * Directive that enables context accumulation in the scala compiler and uses this information to provide
 * a scoping context lookup API given a current tree. Context lookup is provided through the [[lookupContext]]
 * method that retrieves the context associated to a given `tree`.
 *
 * This directive is useful for rules that need to access scoping information given `String` symbol names
 * that haven't been typechecked and bound to symbols by the typer run.
 *
 * /!\ WARNING /!\ The [[lookupContext]] method will NOT work on trees that don't have a valid position
 * associated to them! Notable examples are:
 * - EmptyTree
 * - noSelfType
 * - pendingSuperCalls
 * - TypeTree() if tree.tpe == NoType
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
    /** Associated tree */
    lazy val tree: universe.Tree = context.tree.asInstanceOf[universe.Tree]

    /** Parent context */
    lazy val outer: ScopingContext = if (_outer == null) NoScopingContext else _outer

    /** Containing symbol (e.g. class, method, val, etc.) */
    lazy val owner: universe.Symbol = context.owner.asInstanceOf[universe.Symbol]

    /** Symbol lookup based on searched `name` and filter `suchThat` */
    def lookupSymbol(name: universe.Name, suchThat: universe.Symbol => Boolean): universe.NameLookup = {
      context.lookupSymbol(name.asInstanceOf[global.Name], suchThat.asInstanceOf[global.Symbol => Boolean]).asInstanceOf[universe.NameLookup]
    }

    override def toString: String = context.toString
  }

  // map from scala compiler contexts to our contexts that is used to build context tree in `ScopingContext`
  private val contexts = MutableMap.empty[global.analyzer.Context, ScopingContext]

  // ScopingContext wrapper for global.analyzer.NoContext
  object NoScopingContext extends ScopingContext(global.analyzer.NoContext, null)

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

  private type Contexts = ArrayBuffer[ContextTree]
  private val contextBuffers = MutableMap.empty[SourceFile, Contexts]

  /**
   * A context tree contains contexts that are indexed by positions.
   *  It satisfies the following properties:
   *  1. All context come from compiling the same unit.
   *  2. Child contexts have parent contexts in their outer chain.
   *  3. The `pos` field of a context is the same as `context.tree.pos`, unless that
   *     position is transparent. In that case, `pos` equals the position of
   *     one of the solid descendants of `context.tree`.
   *  4. Children of a context have non-overlapping increasing positions.
   *  5. No context in the tree has a transparent position.
   */
  private class ContextTree(val pos: Position, val context: ScopingContext, val children: Contexts) {
    def this(pos: Position, context: ScopingContext) = this(pos, context, new Contexts)
    override def toString = "ContextTree(" + pos + ", " + children + ")"
  }

  /**
   * Returns the most precise context possible for the given `pos`.
   *
   *  It looks for the finest ContextTree containing `pos`, and then look inside
   *  this ContextTree for a child ContextTree located immediately before `pos`.
   *  If such a child exists, returns its context, otherwise returns the context of
   *  the parent ContextTree.
   *
   *  This is required to always return a context which contains the all the imports
   *  declared up to `pos` (see SI-7280 for a test case).
   *
   *  Can return None if `pos` is before any valid Scala code.
   */
  private def locateContext(tree: Tree): Option[ScopingContext] = {
    @tailrec
    def locateFinestContextTree(context: ContextTree): ContextTree = {

      // we assume here that there cannot be two different trees with the same position and same class
      // I'm not quite sure this holds...
      // we also return early for contexts associated to TypeTrees since these get inserted into the AST after
      // typer so we won't have the correct class associated in the position tree
      lazy val potentialChild = context.pos == tree.pos && {
        val ctree = context.context.tree
        ctree.getClass != tree.getClass && tree.getClass != classOf[TypeTree]
      }

      if ((context.pos includes tree.pos) || potentialChild) {
        locateContextTree(context.children, tree) match {
          case Some(x) =>
            locateFinestContextTree(x)
          case None =>
            context
        }
      }
      else {
        context
      }
    }
    contextBuffers.get(tree.pos.source) flatMap { contexts =>
      locateContextTree(contexts, tree) map locateFinestContextTree map (_.context)
    }
  }

  /**
   * Returns the ContextTree containing `tree`, or the ContextTree positioned just before `tree`,
   *  or None if `tree` is located before all ContextTrees.
   */
  private def locateContextTree(contexts: Contexts, tree: Tree): Option[ContextTree] = {
    val pos = tree.pos
    if (contexts.isEmpty) None
    else {
      // binary search on contexts, loop invar: lo <= hi, recursion metric: `hi - lo`
      @tailrec
      def loop(lo: Int, hi: Int, previousSibling: Option[ContextTree]): Option[ContextTree] = {
        // [SI-8239] enforce loop invariant & ensure recursion metric decreases monotonically on every recursion
        if (lo > hi) previousSibling
        else if (pos properlyPrecedes contexts(lo).pos)
          previousSibling
        else if (contexts(hi).pos properlyPrecedes pos)
          Some(contexts(hi))
        else {
          val mid = (lo + hi) / 2
          val midpos = contexts(mid).pos
          val midtree = contexts(mid).context.tree

          if ((midpos includes pos) || (midpos == pos))
            Some(contexts(mid))
          else if (midpos properlyPrecedes pos)
            // recursion metric: (hi - ((lo + hi)/2 + 1)) < (hi - lo)
            // since (hi - ((lo + hi)/2 + 1)) - (hi - lo) = lo - ((lo + hi)/2 + 1) < 0
            // since 2*lo - lo - hi - 2 = lo - hi - 2 < 0
            // since lo < hi + 2
            // can violate lo <= hi, hence the lo > hi check at the top [SI-8239]
            loop(mid + 1, hi, Some(contexts(mid)))
          else if (lo != hi) // avoid looping forever (lo == hi violates the recursion metric) [SI-8239]
            // recursion metric: ((lo + hi)/2) - lo < (hi - lo)
            // since ((lo + hi)/2) - lo - (hi - lo) = ((lo + hi)/2) - hi < 0
            // since 2 * (((lo + hi)/2) - hi) = lo - hi < 0 since lo < hi
            loop(lo, mid, previousSibling)
          else previousSibling
        }
      }
      loop(0, contexts.length - 1, None)
    }
  }

  /**
   * Insert a context at correct position into a buffer of context trees.
   *  If the `context` has a transparent position, add it multiple times
   *  at the positions of all its solid descendant trees.
   */
  private def addContext(context: ScopingContext): Unit = {
    val cpos = context.tree.pos
    if (cpos.source != NoSourceFile) {
      val buffer = contextBuffers.getOrElseUpdate(cpos.source, new Contexts)
      if (cpos.isTransparent)
        for (t <- context.tree.children flatMap solidDescendants)
          addContext(buffer, context, t.pos)
      else
        addContext(buffer, context, cpos)
    }
  }

  /**
   * Insert a context with non-transparent position `cpos`
   *  at correct position into a buffer of context trees.
   */
  private def addContext(contexts: Contexts, context: ScopingContext, cpos: Position): Unit = {
    try {
      if (!cpos.isDefined) {}
      else if (contexts.isEmpty)
        contexts += new ContextTree(cpos, context)
      else {
        val hi = contexts.length - 1
        if (contexts(hi).pos precedes cpos)
          contexts += new ContextTree(cpos, context)
        else if (contexts(hi).pos properlyIncludes cpos) // fast path w/o search
          addContext(contexts(hi).children, context, cpos)
        else if (cpos precedes contexts(0).pos)
          new ContextTree(cpos, context) +=: contexts
        else {
          def insertAt(idx: Int): Boolean = {
            val oldpos = contexts(idx).pos
            if (oldpos sameRange cpos) {
              contexts(idx) = new ContextTree(cpos, context, contexts(idx).children)
              true
            }
            else if (oldpos includes cpos) {
              addContext(contexts(idx).children, context, cpos)
              true
            }
            else if (cpos includes oldpos) {
              val start = contexts.indexWhere(cpos includes _.pos)
              val last = contexts.lastIndexWhere(cpos includes _.pos)
              contexts(start) = new ContextTree(cpos, context, contexts.slice(start, last + 1))
              contexts.remove(start + 1, last - start)
              true
            }
            else if (oldpos == cpos) {
              val oldtree = contexts(idx).context.tree
              val ctree = context.tree
              if (oldtree.exists(_ == ctree)) {
                addContext(contexts(idx).children, context, cpos)
                true
              }
              else if (ctree.exists(_ == oldtree)) {
                val start = contexts.indexWhere(c => (cpos precedes c.pos) && ctree.exists(_ == c.context.tree))
                val last = contexts.lastIndexWhere(c => (c.pos precedes cpos) && ctree.exists(_ == c.context.tree))
                contexts(start) = new ContextTree(cpos, context, contexts.slice(start, last + 1))
                contexts.remove(start + 1, last - start)
                true
              }
              else false
            }
            else false
          }
          def loop(lo: Int, hi: Int): Unit = {
            if (hi - lo > 1) {
              val mid = (lo + hi) / 2
              val midpos = contexts(mid).pos
              if (cpos precedes midpos)
                loop(lo, mid)
              else if (midpos precedes cpos)
                loop(mid, hi)
              else
                addContext(contexts(mid).children, context, cpos)
            }
            else if (!insertAt(lo) && !insertAt(hi)) {
              val lopos = contexts(lo).pos
              val hipos = contexts(hi).pos
              if ((lopos precedes cpos) && (cpos precedes hipos))
                contexts.insert(hi, new ContextTree(cpos, context))
              else
                inform("internal error? skewed positions: " + lopos + " !< " + cpos + " !< " + hipos)
            }
          }
          loop(0, hi)
        }
      }
    }
    catch {
      case ex: Throwable =>
        println(ex)
        ex.printStackTrace()
        println("failure inserting " + cpos + " into " + contexts + "/" + contexts(contexts.length - 1).pos + "/" +
          (contexts(contexts.length - 1).pos includes cpos))
        throw ex
    }
  }

  /**
   * AnalyzerPlugin injected into the compiler that gets `pluginsTyped` called on typed tree. We use
   * this to extract typing contexts and wrap them into ScopingContext instances that can be presented
   * to rule writers.
   *
   * Unfortunately, `pluginsTyped` doesn't get called on all ASTs during typing, so holes need to be
   * patched. We use the incremental compiler context accumulation code to assign contexts to positions
   * and extend the technique with handling of non-range positions. The extension is probably less
   * efficient but more general (and is a fallback in case range positions exist).
   */
  private class ScopingPlugin extends global.analyzer.AnalyzerPlugin {
    import global._
    import global.analyzer._

    override def pluginsTyped(tpe: Type, typer: Typer, tree: Tree, mode: Mode, pt: Type): Type = {
      val context = getContext(typer.context)
      addContext(context)
      tpe
    }
  }

  global.analyzer.addAnalyzerPlugin(new ScopingPlugin)

  /** Retrieve `ScopingContext` based on current `tree`. */
  def lookupContext(tree: Tree): ScopingContext = {
    locateContext(tree).getOrElse(NoScopingContext)
  }

}

