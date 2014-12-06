package scala.reflect.internal.traversal

import scala.reflect.macros._
import scala.language.experimental.macros

/**
 * OptimizingMacros
 *
 * Provides utility methods for extracting the case statement structure of a partial
 * function and accessing the types of the trees we're matching on. If all types are
 * known, they are returned and can be used to speed up traversal by skipping all
 * trees that don't match the types we're interested in.
 */
object OptimizingMacros {

  /** Actual extraction method */
  def extractClasses(c: blackbox.Context)(trees: List[c.Tree]): Option[Set[c.Tree]] = {

    import c.universe._

    object Selection {
      private def selection(select: Select): Option[(Tree, Seq[String])] = select match {
        case Select(tree, name) if tree.tpe <:< typeOf[scala.reflect.api.Universe] =>
          Some(tree -> Seq(name.toString))
        case Select(tree: Select, name) =>
          selection(tree).map(p => p._1 -> (p._2 :+ name.toString))
        case _ => None
      }

      def unapply(select: Select): Option[(Tree, String, String, String)] = selection(select) match {
        case Some((tree, names)) if names.size == 3 => Some((tree, names(0), names(1), names(2)))
        case _                                      => None
      }
    }

    def treeClasses(u: Tree, classes: String*): Set[Tree] = classes.map { cls =>
      if (cls.endsWith("$")) {
        q"$u.${TermName(cls.substring(0, cls.length - 1))}.getClass"
      }
      else {
        q"classOf[$u.${TypeName(cls)}]"
      }
    }.toSet

    /**
     * /!\ WARNING /!\
     *
     * Since we are using class matching for traversal optimization and quasiquotes use subtype matching,
     * there can be issues with matching tree subtypes! This is managed by using class sets for matching
     * so all sub-classes of the one matched by subtype matching in the quasiquote will be added to the
     * class-to-traversal map.
     *
     * All new conversion rules (ie. case statement) should be accompanied by at least one test in
     * test/MacroTest.scala to make sure no trees are skipped by the subtype matching!
     *
     * DON'T FORGET TO WRITE TESTS FOR NEW CONVERSIONS
     */
    def treeToClass(t: Tree): Set[Tree] = t match {
      case Selection(universe, "internal", "reificationSupport", "SyntacticMatch") =>
        treeClasses(universe, "Match")

      case Selection(universe, "internal", "reificationSupport", "SyntacticVarDef") =>
        treeClasses(universe, "ValDef")

      case Selection(universe, "internal", "reificationSupport", "SyntacticValDef") =>
        treeClasses(universe, "ValDef", "noSelfType$")

      case Selection(universe, "internal", "reificationSupport", "SyntacticDefDef") =>
        treeClasses(universe, "DefDef")

      case Selection(universe, "internal", "reificationSupport", "SyntacticAssign") =>
        treeClasses(universe, "Assign")

      case Selection(universe, "internal", "reificationSupport", "SyntacticSelectTerm") =>
        treeClasses(universe, "Select")

      case Selection(universe, "internal", "reificationSupport", "SyntacticApplied") =>
        treeClasses(universe, "UnApply", "Apply", "ApplyToImplicitArgs", "ApplyImplicitView", "pendingSuperCall$")

      case Selection(universe, "internal", "reificationSupport", "SyntacticForYield") =>
        treeClasses(universe, "Apply", "ApplyToImplicitArgs", "ApplyImplicitView")

      case Selection(universe, "internal", "reificationSupport", "SyntacticFor") =>
        treeClasses(universe, "Apply", "ApplyToImplicitArgs", "ApplyImplicitView")

      case _ =>
        c.warning(t.pos, "Unmanaged quasiquote: " + t + "\n" +
          "You can file this warning as a bug report at https://github.com/scala/scala-abide")
        Set.empty
    }

    object Reference {
      private def reference(tpe: Type): Option[(Type, Seq[String])] = tpe match {
        case tpe if tpe <:< typeOf[scala.reflect.api.Universe] =>
          Some((tpe -> Seq.empty[String]))
        case TypeRef(pre, sym, _) =>
          reference(pre).map(p => p._1 -> (p._2 :+ sym.name.toString))
        case _ => None
      }

      def unapply(tp: Type): Option[(Type, String)] = reference(tp) match {
        case Some((tpe, names)) if names.size == 1 => Some((tpe, names(0)))
        case _                                     => None
      }
    }

    def typeClasses(tpe: Type, members: String*): Set[Tree] = members.map { m =>
      val memberType = if (m.endsWith("$")) {
        val termName = TermName(m.substring(0, m.length - 1))
        val memberObj = tpe.members.find(_.name == termName).get
        memberObj.asModule.moduleClass.asType.toType
      }
      else {
        val typeName = TypeName(m)
        tpe.members.find(_.name == typeName).get.asType.toType
      }
      q"scala.reflect.classTag[$memberType].runtimeClass"
    }.toSet

    /**
     * /!\ WARNING /!\
     *
     * Since we are using class matching for traversal optimization and we are extracting subtype matches
     * in the macro, there can be issues with matching tree subtypes!
     *
     * See the warning in [[treeToClass]] for more.
     *
     * DON'T FORGET TO WRITE TESTS FOR NEW CONVERSIONS
     */
    def typeToClass(tree: Tree, t: Type): Set[Tree] = t match {
      case Reference(tpe, "Bind") =>
        typeClasses(tpe, "Bind")

      case Reference(tpe, "Match") =>
        typeClasses(tpe, "Match")

      case Reference(tpe, "Ident") =>
        typeClasses(tpe, "Ident")

      case Reference(tpe, "Select") =>
        typeClasses(tpe, "Select")

      case Reference(tpe, "DefDef") =>
        typeClasses(tpe, "DefDef")

      case Reference(tpe, "ValDef") =>
        typeClasses(tpe, "ValDef", "noSelfType$")

      case Reference(tpe, "Assign") =>
        typeClasses(tpe, "Assign")

      case Reference(tpe, "Apply") =>
        typeClasses(tpe, "Apply", "ApplyToImplicitArgs", "ApplyImplicitView", "pendingSuperCall$")

      case Reference(tpe, "ClassDef") =>
        typeClasses(tpe, "ClassDef")

      case Reference(tpe, "ModuleDef") =>
        typeClasses(tpe, "ModuleDef")

      case _ =>
        c.warning(tree.pos, "Unmanaged type: " + t + "\n" +
          "You can file this warning as a bug report at https://github.com/scala/scala-abide")
        Set.empty
    }

    def extractorToClass(t: Tree): Set[Tree] = {
      val extractor = t match {
        case cq"$bind @ $ex if $guard => $res" => Some(ex)
        case cq"$ex if $guard => $res"         => Some(ex)
        case _                                 => None
      }

      extractor match {
        case Some(UnApply(q"$obj.unapply($arg)", _)) => obj match {
          case q"$mods class $nm1 { ..$defs }; new $nm2()" => defs.flatMap {
            case q"def unapply(..$args) : $ret = $scrut match { case ..$cases }" =>
              cases.flatMap(extractorToClass(_))
            case _ => Set.empty
          }.toSet

          case tree =>
            treeToClass(tree)
        }

        case Some(Apply(caller, _)) if caller.tpe != null =>
          typeToClass(caller, caller.tpe.resultType)

        case Some(Ident(name)) if name == termNames.WILDCARD =>
          Set.empty

        case Some(Typed(_, tpt)) if tpt.tpe != null =>
          typeToClass(tpt, tpt.tpe)

        case _ =>
          println("extractor=" + extractor + " : " + extractor.map(_.getClass))
          Set.empty
      }
    }

    val allClasses = trees.map(extractorToClass(_))
    if (allClasses.forall(_.nonEmpty)) Some(allClasses.flatten.toSet) else None
  }

  /**
   * Extract the classes from a traditional partial function from trees to
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
  def optimize_impl(c: blackbox.Context)(pf: c.Tree) = {

    import c.universe._

    val classes: Option[Set[Tree]] = pf match {
      case q"{ case ..$cases }" => extractClasses(c)(cases)
      case _                    => None
    }

    q"ClassExtraction($classes, $pf)"
  }
}

/**
 * Traversal class that provides the optimize macro which extracts the class information
 * needed by [[TraversalFusion]] to actually perform fusing.
 *
 * @see [[Traversal]]
 * @see [[TraversalFusion]]
 */
trait OptimizingTraversal extends Traversal {
  import universe._

  case class ClassExtraction(
      classes: Option[Set[Class[_]]],
      pf: PartialFunction[Tree, Unit]
  ) extends PartialFunction[Tree, Unit] {
    def isDefinedAt(tree: Tree): Boolean = pf.isDefinedAt(tree)
    def apply(tree: Tree): Unit = pf.apply(tree)
  }

  def optimize(pf: PartialFunction[Tree, Unit]): PartialFunction[Tree, Unit] = macro OptimizingMacros.optimize_impl
}

