package scala.tools.abide.test.traversal

import scala.tools.abide._
import scala.tools.abide.test._
import scala.tools.abide.traversal._
import scala.tools.abide.directives._
import scala.reflect.internal.traversal._

trait TraversalTest extends AbideTest {
  
  val context = new Context(global) with MutabilityChecker

  def apply(rule : TraversalRule)(tree : global.Tree) : List[rule.Warning] = {
    rule.traverse(tree.asInstanceOf[rule.universe.Tree])
    rule.result.warnings
  }

  def apply(rules : TraversalRule*)(tree : global.Tree) : List[Warning] = {
    val ruleCast = rules.map(_.asInstanceOf[Traversal { val universe : TraversalTest.this.global.type }])
    val fusion = Fuse(global)(ruleCast : _*)
    fusion.traverse(tree)
    rules.flatMap(_.result.warnings).toList
  }
}
