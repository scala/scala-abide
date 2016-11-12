package scala.tools.abide.traversal

import scala.reflect.macros._
import scala.language.experimental.macros

/**
 * WarningExtractor
 *
 * Provide the extraction macro that uses the placeholder [[WarningExtractor.tree]] value in the
 * [[WarningExtractor]] class with to create a function from Tree to String by replacing the
 * dummy tree with the function parameter.
 *
 * To get the String we use the StringContext that we assume is the macro caller and
 * apply the `StringContext.s` method to the transformed arguments. We also assume all
 * references to `_.this.tree` refer to the [[WarningExtractor.tree]] value as checking for
 * enclosing context is non-trivial and discouraged.
 */
object WarningExtractor {

  /**
   * Replacement macro implementation that replaces the placeholders with the returned
   * function's argument tree.
   *
   * We have to use `c.untypecheck` on the `args` in to keep the ref-checks phase from
   * crashing when traversing these trees. This should not be an issue as all types are pretty
   * obvious here and typer should not be able to infer anything different.
   */
  def w_impl(c: blackbox.Context)(args: c.Tree*): c.Tree = {
    import c.universe._

    val valName: TermName = TermName(c.freshName("thisTree$"))
    val valDef = q"val $valName : this.universe.Tree"

    val cleanArgs = args map (tree => c.untypecheck(tree))
    val newArgs = cleanArgs map (arg => c.internal.transform(arg) {
      (tree, api) =>
        tree match {
          case Select(This(_), TermName("tree")) => Ident(valDef.name)
          case _                                 => api.default(tree)
        }
    })

    val sc = c.macroApplication match {
      case Apply(Select(Apply(_, List(ctx)), TermName("w")), b) => ctx
      case _ => c.abort(c.enclosingPosition, "Unexpected application, should be of the shape `w\"some text with $tree\"`")
    }

    q"($valDef => $sc.s(..$newArgs))"
  }
}

/**
 * WarningExtractor
 *
 * The trait that wraps the placeholder replacement macro and provides the
 * StringContext wrapper that offers the `w` method.
 */
trait WarningExtractor {
  val universe: Universe
  import universe._

  /**
   * The placeholder tree that will be replaced by the parameter tree by the
   * [[WarningHelper.w]] macro. We throw an exception if the value is read as
   * this should never be the case!
   */
  final def tree: Tree = scala.sys.error("Should never happen")

  /** StringContext wrapper that provides the `w` interpolator */
  implicit class WarningHelper(val sc: StringContext) {
    def w(args: Any*): (Tree => String) = macro WarningExtractor.w_impl
  }
}
