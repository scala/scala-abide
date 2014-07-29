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

  private type Leaver = (Traversal, (Nothing => Any))

  private def foreach(tree: Tree)(enter: Tree => List[Leaver]): Unit = {
    def rec(tree: Tree): Unit = {
      val leavers = enter(tree)
      tree.children.foreach(rec(_))
      leavers.foreach {
        case (traversal, leaver) =>
          traversal.transform(leaver.asInstanceOf[traversal.State => traversal.State])
      }
    }

    rec(tree)
  }

  override def traverse(tree: Tree): Unit = {
    traversals.foreach(_.init)

    def enter(tree: Tree): List[Leaver] = getTraversals(tree).flatMap { traversal =>
      val defined = traversal.apply(tree)

      val leaver: Option[Nothing => Any] = if (!defined) None else traversal match {
        case scoper: ScopingTraversal => scoper.consumeLeaver
        case _                        => None
      }

      leaver.map(l => traversal -> l)
    }

    foreach(tree)(enter)
  }
}

