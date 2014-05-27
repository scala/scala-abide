package scala.tools.abide
package traversal

import scala.tools.nsc._

trait TraversalStep[T <: Global#Tree, S <: State] {
  val enter : S => S
  val leave : Option[S => S]

  def and(that : TraversalStep[T,S]) : TraversalStep[T,S] = new TraversalStep[T,S] {
    val enter : S => S = that.enter compose TraversalStep.this.enter
    val leave : Option[S => S] = TraversalStep.this.leave match {
      case Some(leaver1) => that.leave match {
        case Some(leaver2) => Some(leaver2 compose leaver1)
        case None => TraversalStep.this.leave
      }
      case None => that.leave
    }
  }
}

trait SimpleStep[T <: Global#Tree, S <: State] extends TraversalStep[T, S] {
  val leave : Option[S => S] = None
}

case class RuleControl[T <: Global#Tree](skipping : Option[T])

trait Traversal {
  val global : Global
  import global._

  protected[traversal] type RuleType = TraversalRule {
    val traverser : Traversal.this.type
  }

  def traverse(tree : Tree) : List[Warning]
}
