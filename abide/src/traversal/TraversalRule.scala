package scala.tools.abide
package traversal

trait TraversalRule extends Rule { self : Traversal =>
  val universe : analyzer.global.type = analyzer.global
}
