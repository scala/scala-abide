package scala.reflect.internal.traversal

/**
 * TraversalFusion
 * 
 * Takes care of actual traversal fusing.
 */
trait TraversalFusion {
  protected val universe : scala.reflect.api.Universe
  import universe._

  private[traversal] type TraversalType = Traversal { val universe : TraversalFusion.this.universe.type }
  private[traversal] type FusionType = TraversalFusion { val universe : TraversalFusion.this.universe.type }

  /** List of all traversals we're fusing */
  val traversals : Seq[TraversalType]

  /** Adds a new [[Traversal]] to the list of fused traversals */
  def fuse(that : TraversalType) : FusionType = Fuse(universe)(traversals :+ that : _*)

  /** Adds all the traversals contained in a [[TraversalFusion]] to the list of fused traversals */
  def fuse(that : FusionType) : FusionType = Fuse(universe)(traversals ++ that.traversals : _*)

  /**
   * We compute these maps lazily to avoid unnecessary computations during
   * fuse foldings where we don't actually want to use the partial fusings
   * and should therefore not lose time optimizing them
   */
  private lazy val (classToTraversals, allClassTraversals) = {
    var classToTraversals  : Map[Class[_], Set[TraversalType]] = Map.empty
    var allClassTraversals : Set[TraversalType]                = Set.empty

    for (traversal <- traversals) {
      val classes = traversal match {
        case optimizer : OptimizingTraversal => optimizer.step match {
          case optimizer.ClassExtraction(classes, _) => classes
          case _ => None
        }

        case _ => None
      }

      classes match {
        case Some(matchClasses) => matchClasses.foreach { clazz =>
          classToTraversals += (clazz -> (classToTraversals.getOrElse(clazz, Set.empty) + traversal))
        }
        case None =>
          allClassTraversals += traversal
      }
    }

    (classToTraversals, allClassTraversals)
  }

  /** Make sure all internal structures necessary for traversal have been computed */
  def force : this.type = {
    classToTraversals
    allClassTraversals
    this
  }

  /** Access relevant traversals given a concrete tree instance (lookup by class and fallback for un-optimized traversals) */
  protected def getTraversals(tree : Tree) : List[TraversalType] = {
    (classToTraversals.getOrElse(tree.getClass, Nil) ++ allClassTraversals).toList
  }

  private def foreach(tree : Tree)(f : Tree => Unit) {
    def rec(tree : Tree) {
      f(tree)
      tree.children.foreach(rec(_))
    }

    rec(tree)
  }

  /** Applies the fused traversals to a tree (in a foreach manner) */
  def traverse(tree : Tree) {
    traversals.foreach(_.init)
    foreach(tree)(tree => getTraversals(tree).foreach { traversal =>
      if (traversal.step.isDefinedAt(tree)) traversal.step.apply(tree)
    })
  }

}
