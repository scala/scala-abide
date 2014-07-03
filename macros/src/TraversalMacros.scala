package scala.reflect.internal.traversal

import scala.reflect.macros._
import scala.language.experimental.macros

/**
 * TraversalMacros
 * 
 * Provides utility methods for extracting the case statement structure of a partial
 * function and accessing the types of the trees we're matching on. If all types are
 * known, they are returned and can be used to speed up traversal by skipping all
 * trees that don't match the types we're interested in.
 */
object TraversalMacros {

  /** Actual extraction method */
  def extractClasses(c : blackbox.Context)
                    (trees : List[c.Tree]) : Option[List[c.Tree]] = {

    import c.universe._

    object Selection {
      private def selection(select : Select) : Option[(Tree, Seq[String])] = select match {
        case Select(tree, name) if tree.tpe <:< typeOf[scala.reflect.api.Universe] =>
          Some(tree -> Seq(name.toString))
        case Select(tree : Select, name) =>
          selection(tree).map(p => p._1 -> (p._2 :+ name.toString))
        case _ => None
      }

      def unapply(select : Select) : Option[(Tree, String, String, String)] = selection(select) match {
        case Some((tree, names)) if names.size == 3 => Some((tree, names(0), names(1), names(2)))
        case _ => None
      }
    }

    def treeToClass(t: Tree) : Option[Tree] = t match {
      case Selection(universe, "internal", "reificationSupport", "SyntacticMatch"     ) => Some(q"classOf[$universe.Match]" )
      case Selection(universe, "internal", "reificationSupport", "SyntacticVarDef"    ) => Some(q"classOf[$universe.ValDef]")
      case Selection(universe, "internal", "reificationSupport", "SyntacticValDef"    ) => Some(q"classOf[$universe.ValDef]")
      case Selection(universe, "internal", "reificationSupport", "SyntacticDefDef"    ) => Some(q"classOf[$universe.DefDef]")
      case Selection(universe, "internal", "reificationSupport", "SyntacticAssign"    ) => Some(q"classOf[$universe.Assign]")
      case Selection(universe, "internal", "reificationSupport", "SyntacticApplied"   ) => Some(q"classOf[$universe.Apply]" )
      case Selection(universe, "internal", "reificationSupport", "SyntacticSelectTerm") => Some(q"classOf[$universe.Select]")
      case _ =>
        println("Unmanaged quasiquote: " + t)
        None
    }

    object Reference {
      private def reference(tpe : Type) : Option[(Type, Seq[String])] = tpe match {
        case tpe if tpe <:< typeOf[scala.reflect.api.Universe] =>
          Some((tpe -> Seq.empty[String]))
        case TypeRef(pre, sym, _) =>
          reference(pre).map(p => p._1 -> (p._2 :+ sym.name.toString))
        case _ => None
      }

      def unapply(tp : Type) : Option[(Type, String)] = reference(tp) match {
        case Some((tpe, names)) if names.size == 1 => Some((tpe, names(0)))
        case _ => None
      }
    }

    def typeToClass(t: Type) : Option[Tree] = t match {
      case Reference(tpe, "Select") => Some(q"scala.reflect.classTag[$t].runtimeClass")
      case Reference(tpe, "Ident" ) => Some(q"scala.reflect.classTag[$t].runtimeClass")
      case Reference(tpe, "DefDef") => Some(q"scala.reflect.classTag[$t].runtimeClass")
      case _ =>
        println("Unmanaged type: " + t)
        None
    }

    def extractorToClass(t: Tree) : Option[Tree] = {
      val extractor = t match {
        case cq"$bind @ $ex if $guard => $res" => Some(ex)
        case cq"$ex if $guard => $res" => Some(ex)
        case _ => None
      }

      extractor match {
        case Some(UnApply(q"$obj.unapply($arg)", _)) => obj match {
          case q"$mods class $nm1 { ..$defs }; new $nm2()" => defs.map(_ match {
              case q"def unapply(..$args) : $ret = $scrut match { case ..$cases }" =>
                cases.map(extractorToClass(_)).find(_.isDefined).flatten
              case _ => None
            }).find(_.isDefined).flatten

          case tree =>
            treeToClass(tree)
        }

        case Some(Apply(caller, _)) if caller.tpe != null =>
          typeToClass(caller.tpe.resultType)

        case Some(Ident(name)) if name == termNames.WILDCARD =>
          None

        case Some(Typed(_, tpt)) if tpt.tpe != null =>
          typeToClass(tpt.tpe)

        case _ =>
          println("extractor=" + extractor + " : " + extractor.map(_.getClass))
          None
      }
    }

    val allClasses = trees.map(extractorToClass(_))
    if (allClasses.forall(_.isDefined)) Some(allClasses.map(_.get)) else None
  }

  /** Extract the classes from a traditional partial function from trees to
    * traversal steps. For example, the following snippet
    * 
    *   optimize {
    *     case dd : DefDef => ...
    *     case vd : ValDef => ...
    *   }
    * 
    * will have the types DefDef and ValDef extracted from it and will not
    * match any other trees.
    */
  def optimize_impl(c : blackbox.Context)
                   (pf : c.Tree) = {

    import c.universe._

    val classes : Option[List[Tree]] = pf match {
      case q"{ case ..$cases }" => extractClasses(c)(cases)
      case _ => None
    }

    q"NextExpansion($classes, $pf)"
  }

  /** Extract the classes from a function that gives access to state in the
    * traversal step. The function must have the following shape:
    * 
    *   optimize {
    *     state => {
    *       case dd : DefDef => 
    *       case vd : ValDef =>
    *     }
    *   }
    */
  def optimizeStateful_impl(c : blackbox.Context)
                           (f : c.Tree) = {

    import c.universe._

    f match {
      case q"($stateArg) => { case ..$cases }" =>
        val classes = extractClasses(c)(cases)
        q"StateExpansion($classes, $f)"
      case _ =>
        q"StateExpansion(None, $f)"
    }
  }
}

trait TraversalMacros extends Traversal {
  import universe._
  def maintain : TraversalStep[Tree, State] = new SimpleStep[Tree, State] {
    val enter : State => State = x => x
  }
}

/** SimpleTraversal
  * 
  * Wrapper class for TraversalMacros.optimize_impl
  */
trait SimpleTraversal extends TraversalMacros {
  import universe._

  type Step = PartialFunction[Tree, TraversalStep[Tree, State]]

  case class NextExpansion (
    classes : Option[List[Class[_]]],
    pf      : PartialFunction[Tree, TraversalStep[Tree,State]]
  ) extends PartialFunction[Tree,TraversalStep[Tree,State]] {
    def isDefinedAt(tree : Tree) : Boolean = pf.isDefinedAt(tree)
    def apply(tree : Tree) : TraversalStep[Tree,State] = pf.apply(tree)
  }

  def optimize(pf : PartialFunction[Tree, TraversalStep[Tree, State]]) :
              PartialFunction[Tree, TraversalStep[Tree, State]] = macro TraversalMacros.optimize_impl

  lazy val lifted = step.lift

  def apply(tree : Tree, state : State) : Option[(State, Option[State => State])] = {
    lifted.apply(tree).map(step => step.enter(state) -> step.leave)
  }
}

/* HierarchicTraversal
 * 
 * Wrapper class for TraversalMacros.optimizeStateful_impl
 */
trait HierarchicTraversal extends TraversalMacros {
  import universe._

  type Step = State => PartialFunction[Tree, TraversalStep[Tree, State]]

  case class StateExpansion (
    classes : Option[List[Class[_]]],
    f       : State => PartialFunction[Tree,TraversalStep[Tree,State]]
  ) extends (State => PartialFunction[Tree,TraversalStep[Tree,State]]) {
    def apply(state : State) : PartialFunction[Tree,TraversalStep[Tree,State]] = f.apply(state)
  }

  def optimize(f : State => PartialFunction[Tree, TraversalStep[Tree, State]]) : 
              State => PartialFunction[Tree, TraversalStep[Tree, State]] = macro TraversalMacros.optimizeStateful_impl

  def apply(tree : Tree, state : State) : Option[(State, Option[State => State])] = {
    step.apply(state).lift.apply(tree).map(step => step.enter(state) -> step.leave)
  }
}
