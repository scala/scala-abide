package scala.tools.abide
package traversal

/**
 * FusingTraversals
 * 
 * Fuses multiple traversals in a way that increases overall traversal speed.
 * We use the type information we extracted in Traversals with TraversalMacros
 * to quickly determine which rules should be applied to the tree we're currently
 * visiting.
 *
 * @see Traversal
 * @see TraversalMacros
 */
trait FusingTraversals {
  val global : scala.reflect.api.Universe
  import global._

  type TraversalType = Traversal { val analyzer : FusingTraversals.this.type }

  def fuse(traversals : TraversalType*) : FusedTraversal = {
    assert(traversals.nonEmpty, "Cannot fuse empty list of traversals")
    val traversal = traversals.head
    val tail = traversals.tail.toSeq
    traversal fuse (tail : _*)
  }

  /**
   * FusedTraversal
   * 
   * Takes care of actual traversal fusing. We need this two tiered approach
   * (ie. FusingTraversals containing FusedTraversal) to have the same structure
   * as scala.tools.abide.Analyzer and get structural types to allign correctly.
   */
  trait FusedTraversal {

    /** List of all traversals we're fusing */
    val traversals : Seq[TraversalType]

    /** List of all initial states (same order as the traversals seq */
    val emptyStates : Seq[_]

    def fuse(that : TraversalType) : FusedTraversal = new FusedTraversal {
      val traversals = FusedTraversal.this.traversals :+ that
      val emptyStates = FusedTraversal.this.emptyStates :+ that.emptyState
    }

    def fuse(that : FusedTraversal) : FusedTraversal = new FusedTraversal {
      val traversals = FusedTraversal.this.traversals ++ that.traversals
      val emptyStates = FusedTraversal.this.emptyStates ++ that.emptyStates
    }

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
          case sm : SimpleTraversal => sm.step match {
            case sm.NextExpansion(classes, _) => classes
            case _ => None
          }
          
          case hm : HierarchicTraversal => hm.step match {
            case hm.StateExpansion(classes, _) => classes
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

    private def getTraversals(clazz : Class[_]) : List[TraversalType] = {
      (classToTraversals.getOrElse(clazz, Nil) ++ allClassTraversals).toList
    }

    /**
     * FusedResult
     * 
     * Since scala doesn't have have heterogeneous maps, we keep the dependently typed
     * traversal result (result of a traversal depends on traversal type) in a map to Any
     * and cast the results when necessary. Types are ensured by the framework.
     */
    class FusedResult private[FusingTraversals](val mapping : Map[TraversalType, _]) {
      def apply(traversal : TraversalType) : traversal.State = mapping.get(traversal) match {
        case Some(state) => state.asInstanceOf[traversal.State]
        case None => scala.sys.error("Extracting result of traversal that wasn't part of this fused run")
      }

      def toSeq : Seq[_] = traversals.map(t => mapping(t))
    }

    /** Main method of traversal class that provides efficient traversal */
    def traverse(tree : Tree) : FusedResult = {

      /** Generic DFS fold method that can deal with entering AND leaving nodes */
      def fold[T](tree : Tree, init : T)(enter : (Tree, T) => T, leave : (Tree, T) => T) : T = {
        def rec(tree : Tree, state : T, left : List[Tree]) : (T, List[Tree]) = {
          val leftState = left.foldLeft(state)((state, tree) => leave(tree, state))
          tree.children.foldLeft(enter(tree, leftState) -> List[Tree]()) { case ((t, left), child) =>
            val (newT, newLeft) = rec(child, t, left)
            (newT, newLeft :+ child)
          }
        }

        val (result, finalLefts) = rec(tree, init, Nil)
        (finalLefts :+ tree).foldLeft(result)((state, tree) => leave(tree, state))
      }

      type States = Map[TraversalType, _]
      type Leaver[S] = (TraversalType, S => S)
      type Leavers = StrictMap[Tree, List[Leaver[_]]]

      case class Ctx(states : States, leavers : Leavers)

      def enter(tree : Tree, ctx : Ctx) : Ctx = {
        val Ctx(states, leavers) = ctx

        val traversals = getTraversals(tree.getClass)
        val (newStates, newLeavers) = traversals.foldLeft(states -> List[Leaver[_]]()) {
          case (acc @ (states, leavers), traversal) =>
            traversal(tree, states(traversal).asInstanceOf[traversal.State]) match {
              case None => acc
              case Some((newState, newLeaver)) =>
                val allStates = states + (traversal -> newState)
                val allLeavers = newLeaver match {
                  case Some(leaver) => leavers :+ (traversal -> leaver)
                  case None => leavers
                }

                (allStates, allLeavers)
            }
        }

        val allLeavers = if (newLeavers.nonEmpty) leavers + (tree -> newLeavers) else leavers
        Ctx(states ++ newStates, allLeavers)
      }

      def leave(tree : Tree, ctx : Ctx) : Ctx = {
        val Ctx(states, leavers) = ctx

        leavers.get(tree) match {
          case None => ctx
          case Some(tl) =>
            val newStates = states ++ tl.map { case (traversal, l) =>
              val leaver = l.asInstanceOf[traversal.State => traversal.State]
              traversal -> leaver(states(traversal).asInstanceOf[traversal.State])
            }

            val newLeavers = leavers - tree
            Ctx(newStates, newLeavers)
        }
      }

      val initialStates  : States  = (traversals zip emptyStates).toMap
      val initialLeavers : Leavers = StrictMap.empty
      val init = Ctx(initialStates, initialLeavers)

      val Ctx(states, leavers) = fold(tree, init)(enter, leave)
      assert(leavers.values.forall(_.isEmpty))

      new FusedResult(states)
    }
  }
}
