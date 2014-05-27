package scala.tools.abide

import traversal._
import directives._
import scala.tools.nsc._
import scala.reflect.internal._

trait Analyzer extends FastTraversal with MutabilityChecker {
  val global : Global with Positions
  import global._

  def apply(tree: Tree) : List[Warning] = {
    traverse(tree)
  }
}
