package scala.reflect.internal.traversal

/**
 * object Fuse
 * 
 * Fuses multiple traversals in a way that increases overall traversal speed.
 * We use the type information we extracted in [[Traversal]] with [[TraversalMacros]]
 * to quickly determine which rules should be applied to the tree we're currently
 * visiting.
 *
 * @see [[Traversal]]
 * @see [[TraversalMacros]]
 */
object Fuse {
  def apply(u  : scala.reflect.api.Universe)
           (ts : Traversal { val universe : u.type }*) :
           TraversalFusion { val universe : u.type } = {
    assert(ts.nonEmpty, "Cannot fuse empty list of traversals")

    if (ts.exists(_.isInstanceOf[ScopingTraversal])) new ScopingTraversalFusion {
      val universe : u.type = u
      val traversals = ts.toSeq
    } else new TraversalFusion {
      val universe : u.type = u
      val traversals = ts.toSeq
    }
  }
}

