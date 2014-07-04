package scala.tools.abide.traversal

import scala.tools.abide._
import scala.reflect.internal.traversal._

trait TraversalRule extends OptimizingTraversal with Rule {
  val universe : context.universe.type = context.universe

  val analyzer = NaiveTraversalAnalyzerGenerator
}
