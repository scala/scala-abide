package scala.tools.abide
package traversal

import scala.tools.nsc._

import scala.reflect.macros._
import scala.language.experimental.macros

object TraversalMacros {

  def extractClasses(c : blackbox.Context)
                    (trees : List[c.Tree])
                    (rule : c.Tree) : Option[List[c.Tree]] = {

    import c.universe._

    val Select(ruleTree, implicitRule) = rule
    assert(implicitRule.toString == "_implicitRule",
      "Implicit rule obtained from wrong definition, probable ambiguity!")

    val globalTree = Select(Select(ruleTree, TermName("traverser")), TermName("global"))

    object Selection {
      def unapplySeq(select : Select) : Option[Seq[String]] = select match {
        case Select(tree, name) if tree equalsStructure globalTree =>
          Some(Seq(name.toString))
        case Select(tree : Select, name) =>
          unapplySeq(tree).map(seq => seq :+ name.toString)
        case _ => None
      }
    }

    def treeToClass(t: Tree) : Option[Tree] = t match {
      case Selection("internal", "reificationSupport", "SyntacticVarDef" ) => Some(q"classOf[$globalTree.ValDef]")
      case Selection("internal", "reificationSupport", "SyntacticValDef" ) => Some(q"classOf[$globalTree.ValDef]")
      case Selection("internal", "reificationSupport", "SyntacticDefDef" ) => Some(q"classOf[$globalTree.DefDef]")
      case Selection("internal", "reificationSupport", "SyntacticAssign" ) => Some(q"classOf[$globalTree.Assign]")
      case Selection("internal", "reificationSupport", "SyntacticApplied") => Some(q"classOf[$globalTree.Apply]" )
      case Selection("internal", "reificationSupport", "SyntacticMatch"  ) => Some(q"classOf[$globalTree.Match]" )
      case _ =>
        println(t)
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
        case st : SingleType if refEq(st, globalTree) => Some(Seq.empty)
        case TypeRef(pre, sym, _) => unapplySeq(pre).map(seq => seq :+ sym.name.toString)
        case _ => None
      }
    }

    def typeToClass(t: Type) : Option[Tree] = t match {
      case Reference("Select") => Some(q"classOf[$globalTree.Select]")
      case Reference("Ident" ) => Some(q"classOf[$globalTree.Ident]" )
      case _ =>
        println(t)
        None
    }

    def extractorToClass(t: Tree) : Option[Tree] = {
      val extractor = t match {
        case cq"$_ @ $ex if $_ => $_" => Some(ex)
        case cq"$ex if $_ => $_" => Some(ex)
        case _ => None
      }

      extractor match {
        case Some(UnApply(q"$obj.unapply($_)", _)) => obj match {
          case q"$mods class $nm1 { ..$defs }; new $nm2()" => defs.map(_ match {
              case q"def unapply(..$args) : $ret = $_ match { case ..$cases }" =>
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

        case _ =>
          println(extractor)
          None
      }
    }

    val allClasses = trees.map(extractorToClass(_))
    if (allClasses.forall(_.isDefined)) Some(allClasses.map(_.get)) else None
  }

  def next_impl(c : blackbox.Context)
               (pf : c.Tree)
               (rule : c.Tree) = {

    import c.universe._

    val classes : Option[List[Tree]] = pf match {
      case q"{ case ..$cases }" => extractClasses(c)(cases)(rule)
      case _ => None
    }

    q"NextExpansion($classes, $pf)"
  }

  def withState_impl(c : blackbox.Context)
                    (f : c.Tree)
                    (rule : c.Tree) = {

    import c.universe._

    f match {
      case q"($stateArg) => { case ..$cases }" =>
        val classes = extractClasses(c)(cases)(rule)
        q"StateExpansion($classes, $f)"
      case _ =>
        q"StateExpansion(None, $f)"
    }
  }

}

sealed abstract class RuleExpansion[T <: Global#Tree, S <: State] {
  val classes : Option[List[Class[_]]]
  val function : (T, S) => Option[TraversalStep[T,S]]
}

case class NextExpansion[T <: Global#Tree, S <: State]
                        (classes : Option[List[Class[_]]], pf : PartialFunction[T, TraversalStep[T,S]])
                        extends RuleExpansion[T,S] {
  val function = {
    val lifted = pf.lift
    (tree : T, state : S) => lifted(tree)
  }
}

case class StateExpansion[T <: Global#Tree, S <: State]
                         (classes : Option[List[Class[_]]], function : (T, S) => Option[TraversalStep[T,S]])
                         extends RuleExpansion[T,S]

object StateExpansion {
  def apply[T <: Global#Tree, S <: State]
           (classes : Option[List[Class[_]]], f : Function1[S, PartialFunction[T, TraversalStep[T,S]]]) :
           StateExpansion[T, S] = {
    StateExpansion(classes, (tree : T, state : S) => f.apply(state).lift.apply(tree))
  }
}

trait TraversalMacros { self : TraversalRule =>

  /** DO NOT CHANGE!!
    * The implicit MUST POINT TO SELF for the traversal macro definition
    */
  protected implicit final val _implicitRule : self.type with traverser.RuleType =
    self.asInstanceOf[self.type with traverser.RuleType]

  import traverser._
  import global._

  def next(pf : PartialFunction[Tree, TraversalStep[Tree, RuleState]])
          (implicit rule : TraversalRule) :
          RuleExpansion[Tree, RuleState] = macro TraversalMacros.next_impl

  def withState(f : RuleState => PartialFunction[Tree, TraversalStep[Tree, RuleState]])
               (implicit rule : TraversalRule) :
               RuleExpansion[Tree, RuleState] = macro TraversalMacros.withState_impl
}
