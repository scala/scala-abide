package scala.tools.abide
package traversal

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
                    (trees : List[c.Tree])
                    (universe : c.Tree) : Option[List[c.Tree]] = {

    import c.universe._

    val Select(macroTree, TermName("_global")) = universe
    val universeTree = Select(Select(macroTree, TermName("analyzer")), TermName("global"))

    object Selection {
      def unapplySeq(select : Select) : Option[Seq[String]] = select match {
        case Select(tree, name) if tree equalsStructure universeTree =>
          Some(Seq(name.toString))
        case Select(tree : Select, name) =>
          unapplySeq(tree).map(seq => seq :+ name.toString)
        case _ => None
      }
    }

    def treeToClass(t: Tree) : Option[Tree] = t match {
      case Selection("internal", "reificationSupport", "SyntacticVarDef" ) => Some(q"classOf[$universeTree.ValDef]")
      case Selection("internal", "reificationSupport", "SyntacticValDef" ) => Some(q"classOf[$universeTree.ValDef]")
      case Selection("internal", "reificationSupport", "SyntacticDefDef" ) => Some(q"classOf[$universeTree.DefDef]")
      case Selection("internal", "reificationSupport", "SyntacticAssign" ) => Some(q"classOf[$universeTree.Assign]")
      case Selection("internal", "reificationSupport", "SyntacticApplied") => Some(q"classOf[$universeTree.Apply]" )
      case Selection("internal", "reificationSupport", "SyntacticMatch"  ) => Some(q"classOf[$universeTree.Match]" )
      case _ =>
        println("Unmanaged quasiquote: " + t)
        None
    }

    object Reference {
      private def refEq(tpe : Type, tree : Tree) : Boolean = (tpe, tree) match {
        case (SingleType(pre, sym), Select(parent, name)) =>
          sym.name == name && refEq(pre, parent)
        case (ThisType(sym), This(name)) =>
          sym.name == name
        case _ => false
      }

      def unapplySeq(tp : Type) : Option[Seq[String]] = tp match {
        case st : SingleType if refEq(st, universeTree) => Some(Seq.empty)
        case TypeRef(pre, sym, _) => unapplySeq(pre).map(seq => seq :+ sym.name.toString)
        case _ => None
      }
    }

    def typeToClass(t: Type) : Option[Tree] = t match {
      case Reference("Select") => Some(q"classOf[$universeTree.Select]")
      case Reference("Ident" ) => Some(q"classOf[$universeTree.Ident]" )
      case Reference("DefDef") => Some(q"classOf[$universeTree.DefDef]")
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
                   (pf : c.Tree)
                   (universe : c.Tree) = {

    import c.universe._

    val classes : Option[List[Tree]] = pf match {
      case q"{ case ..$cases }" => extractClasses(c)(cases)(universe)
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
                           (f : c.Tree)
                           (universe : c.Tree) = {

    import c.universe._

    f match {
      case q"($stateArg) => { case ..$cases }" =>
        val classes = extractClasses(c)(cases)(universe)
        q"StateExpansion($classes, $f)"
      case _ =>
        q"StateExpansion(None, $f)"
    }
  }
}

trait TraversalMacros extends Traversal {
  import analyzer.global._
  implicit lazy val _global : scala.reflect.api.Universe = analyzer.global

  def maintain : TraversalStep[Tree, State] = new SimpleStep[Tree, State] {
    val enter : State => State = x => x
  }
}

/** SimpleTraversal
  * 
  * Wrapper class for TraversalMacros.optimize_impl
  */
trait SimpleTraversal extends TraversalMacros {
  import analyzer.global._

  type Step = PartialFunction[Tree, TraversalStep[Tree, State]]

  case class NextExpansion (
    classes : Option[List[Class[_]]],
    pf      : PartialFunction[Tree, TraversalStep[Tree,State]]
  ) extends PartialFunction[Tree,TraversalStep[Tree,State]] {
    def isDefinedAt(tree : Tree) : Boolean = pf.isDefinedAt(tree)
    def apply(tree : Tree) : TraversalStep[Tree,State] = pf.apply(tree)
  }

  def optimize(pf : PartialFunction[Tree, TraversalStep[Tree, State]])
              (implicit universe : scala.reflect.api.Universe) :
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
  import analyzer.global._

  type Step = State => PartialFunction[Tree, TraversalStep[Tree, State]]

  case class StateExpansion (
    classes : Option[List[Class[_]]],
    f       : State => PartialFunction[Tree,TraversalStep[Tree,State]]
  ) extends (State => PartialFunction[Tree,TraversalStep[Tree,State]]) {
    def apply(state : State) : PartialFunction[Tree,TraversalStep[Tree,State]] = f.apply(state)
  }

  def optimize(f : State => PartialFunction[Tree, TraversalStep[Tree, State]])
              (implicit universe : scala.reflect.api.Universe) :
              State => PartialFunction[Tree, TraversalStep[Tree, State]] = macro TraversalMacros.optimizeStateful_impl

  def apply(tree : Tree, state : State) : Option[(State, Option[State => State])] = {
    step.apply(state).lift.apply(tree).map(step => step.enter(state) -> step.leave)
  }
}
