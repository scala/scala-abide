package scala.tools.abide.test.rules

import scala.tools.abide._
import scala.tools.abide.test._
import scala.tools.abide.traversal._
import scala.tools.abide.directives._

import scala.reflect.internal.traversal._

trait AnalysisTest extends AbideTest {

  val context = new Context(global) with MutabilityChecker

  def apply(rule : Traversal with TraversalRule)(tree : global.Tree) : List[rule.Warning] =
    rule.traverse(tree.asInstanceOf[rule.universe.Tree]).warnings
}
