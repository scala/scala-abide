package scala.tools.abide.traversal

import scala.tools.abide._
import scala.reflect.internal.traversal._

trait TraversalRule extends Rule { self : Traversal =>
  val universe : context.global.type = context.global

  val analyzer = TraversalAnalyzerGenerator
//  val analyzer = NaiveTraversalAnalyzerGenerator
}
