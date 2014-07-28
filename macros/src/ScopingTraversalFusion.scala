package scala.reflect.internal.traversal

/**
 * ScopingTraversalFusion
 *
 * Extension of [[TraversalFusion]] that supports [[ScopingTraversal]] traversals as well as
 * standard [[Traversal]] traversals. The scoping traversals are handled by tracking `leaver`
 * functions for each traversal which are applied as transformations (@see [[Traversal.transform]]) once
 * the traversal leaves a certain tree node.
 *
 * @see [[ScopingTraversal]]
 */
trait ScopingTraversalFusion extends TraversalFusion {
  import universe._

  private def foreach(tree : Tree)(enter : Tree => Unit, leave : Tree => Unit) {
    def rec(tree : Tree, left : List[Tree]) : List[Tree] = {
      left.foreach(leave(_))
      enter(tree)
      tree.children.foldLeft(List.empty[Tree]) { (left, child) => 
        rec(child, left) :+ child
      }
    }

    val finalLefts = rec(tree, Nil)
    (finalLefts :+ tree).foreach(leave(_))
  }

  override def traverse(tree : Tree) {
    traversals.foreach(_.init)

    val leavers = StrictMap.empty[Tree, List[(Traversal, (Nothing => Any))]].withDefaultValue(Nil)

    def enter(tree : Tree) {
      getTraversals(tree).foreach { traversal =>
        if (traversal.apply(tree)) {
          traversal match {
            case scoper : ScopingTraversal => scoper.consumeLeaver.foreach {
              leaver => leavers(tree) = (traversal -> leaver) :: leavers(tree)
            }

            case _ =>
          }
        }
      }
    }

    def leave(tree : Tree) {
      leavers(tree).foreach { case (traversal, leaver) =>
        traversal.transform(leaver.asInstanceOf[traversal.State => traversal.State])
      }
    }

    foreach(tree)(enter, leave)
  }
}



