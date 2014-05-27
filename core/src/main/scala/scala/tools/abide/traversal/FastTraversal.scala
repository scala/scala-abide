package scala.tools.abide
package traversal

import scala.tools.nsc._

trait FastTraversal extends Traversal {
  val global : Global
  import global._

  private var _rules : List[RuleType] = Nil
  private var _classToRules : Map[Class[_], List[RuleType]] = Map.empty
  private var _allClassRules : List[RuleType] = Nil

  def rules : List[RuleType] = _rules
  def classToRule(clazz: Class[_]) : List[RuleType] = _classToRules.getOrElse(clazz, Nil) ++ _allClassRules

  def register(r : TraversalRule) : this.type = {
    if (r.traverser != this) scala.sys.error("Rule traverser doesn't match current traverser instance")
    val rule = r.asInstanceOf[RuleType]
    _rules :+= rule
    rule.step.classes match {
      case Some(matchClasses) => matchClasses.foreach { clazz =>
        _classToRules += (clazz -> (_classToRules.getOrElse(clazz, Nil) :+ rule))
      }
      case None =>
        _allClassRules :+= rule
    }
    this
  }

  def traverse(tree : Tree) : List[Warning] = {

    def fold[T](tree : Tree, init : T)(enter : (Tree, T) => T, leave : (Tree, T) => T) : T = {
      def rec(tree : Tree, state : T, left : List[Tree]) : (T, List[Tree]) = {
        val leftState = left.foldLeft(state)((state, tree) => leave(tree, state))
        tree.children.foldLeft(enter(tree, leftState) -> List[Tree]()) { case ((t, left), child) =>
          val (newT, newLeft) = rec(child, t, left)
          (newT, child :: newLeft)
        }
      }

      val (result, finalLefts) = rec(tree, init, Nil)
      finalLefts.foldLeft(result)((state, tree) => leave(tree, state))
    }

    type RuleStates = Map[RuleType, State]
    type RuleLeaver[S <: State] = (RuleType, S => S)
    type Leavers = StrictMap[Tree, List[RuleLeaver[_ <: State]]]

    case class Ctx(ruleStates : RuleStates, leavers : Leavers)

    val initialStates : RuleStates = rules.map(r => r -> r.emptyState).toMap
    val initialLeavers : Leavers = StrictMap.empty

    def enter(tree : Tree, ctx : Ctx) : Ctx = {
      val Ctx(ruleStates, leavers) = ctx

      val rules = classToRule(tree.getClass)
      val (newStates, newLeavers) = rules.foldLeft(ruleStates -> List[RuleLeaver[_ <: State]]()) {
        case (acc @ (ruleStates, ruleLeavers), rule) =>
          rule(tree, ruleStates(rule).asInstanceOf[rule.RuleState]) match {
            case None => acc
            case Some((newState, newLeaver)) =>
              val allStates = ruleStates + (rule -> newState)
              val allLeavers = newLeaver match {
                case Some(leaver) => ruleLeavers :+ (rule -> leaver)
                case None => ruleLeavers
              }

              (allStates, allLeavers)
          }
      }

      val allLeavers = if (newLeavers.nonEmpty) leavers + (tree -> newLeavers) else leavers
      Ctx(ruleStates ++ newStates, allLeavers)
    }

    def leave(tree : Tree, ctx : Ctx) : Ctx = {
      val Ctx(ruleStates, leavers) = ctx

      leavers.get(tree) match {
        case None => ctx
        case Some(ruleLeavers) =>
          val newStates = ruleStates ++ ruleLeavers.map { case (rule, l) =>
            val leaver = l.asInstanceOf[rule.RuleState => rule.RuleState]
            rule -> leaver(ruleStates(rule).asInstanceOf[rule.RuleState])
          }

          val newLeavers = leavers - tree
          Ctx(newStates, newLeavers)
      }
    }

    val Ctx(ruleStates, ruleLeavers) = fold(tree, Ctx(initialStates, initialLeavers))(enter, leave)

    /*
    def rec(tree : Tree, parent : Option[Tree], ruleStates : RuleStates, ruleControls : RuleControls) : RuleStates = {

      val rules = classToRule(tree.getClass)
      val (newStates, newControls) = rules.foldLeft(ruleStates -> ruleControls) {
        case (acc @ (ruleStates, ruleControls), rule) =>
          val skipping = ruleControls.get(rule).map(_.skipping).getOrElse(false)
          if (skipping) acc else rule(tree) match {
            case None => acc
            case Some(step) =>
              val state = ruleStates(rule).asInstanceOf[rule.RuleState]
              val newStates = ruleStates + (rule -> step.state(state))
              val newControls = step.control match {
                case Some(ctrl) => ruleControls + (rule -> ctrl)
                case None => ruleControls
              }

              (newStates, newControls)
          }
      }

      val (allStates, allControls) : (RuleStates, RuleControls) =
        (ruleStates ++ newStates, ruleControls ++ newControls)

      tree.children.foldLeft(allStates) { (states, child) =>
        val childStates : RuleStates = rec(child, Some(tree), states, allControls)
        states.map { case (rule, _state) =>
          val state = _state.asInstanceOf[rule.RuleState]
          val childState = childStates(rule).asInstanceOf[rule.RuleState]
          rule -> (state merge childState)
        }
      }
    }

    val initialStates = rules.map(r => r -> r.emptyState).toMap
    val finalStates = rec(tree, None, initialStates, Map.empty)
    */

    (for ((rule, state) <- ruleStates; warning <- state.warnings) yield warning).toList
  }

}
