package scala.reflect.internal.traversal.test

import scala.reflect.internal.traversal._
import org.scalatest._

class MacroTest extends FlatSpec with Matchers with TreeProvider {

  trait TestingTraversal extends OptimizingTraversal {
    val universe : MacroTest.this.global.type = MacroTest.this.global

    type State = Null
    def emptyState = null

    def classes : Option[Set[Class[_]]] = step match {
      case c : ClassExtraction => c.classes
      case _ => None
    }

    def validate(classes : Class[_]*) : Boolean = this.classes == Some(classes.toSet)
  }

  "Extractor macro" should "work on basic quasiquotes" in {
    val traversal = new TestingTraversal {
      import universe._

      val step = optimize {
        case q"$mods val $name : $tpt = $_" =>
        case q"$mods var $name : $tpt = $_" =>
        case q"$mods def $name(..$vparamss) : $tpt = $_" =>
        case q"$scrut match { case ..$cases }" =>
        case q"$a = $b" =>
        case q"$a.selection" =>
        case q"$f(..$args)" =>
        case q"for (..$monads) $block" =>
        case q"for (..$monads) yield $block" =>
      }
    }

    val validation = traversal.validate(
      classOf[traversal.universe.ValDef],
      classOf[traversal.universe.DefDef],
      classOf[traversal.universe.Match],
      classOf[traversal.universe.Assign],
      classOf[traversal.universe.Select],
      classOf[traversal.universe.Apply],
      classOf[traversal.universe.ApplyToImplicitArgs],
      classOf[traversal.universe.ApplyImplicitView],
      classOf[traversal.universe.UnApply]
    )

    validation should be (true)
  }

  it should "work on basic tree matchers" in {
    val traversal = new TestingTraversal {
      import universe._

      val step = optimize {
        case id : Ident =>
        case vd : ValDef =>
        case DefDef(_, _, _, _, _, _) =>
        case m : Match =>
        case a : Assign =>
        case s : Select =>
        case ap : Apply =>
      }
    }

    val validation = traversal.validate(
      classOf[traversal.universe.ValDef],
      classOf[traversal.universe.DefDef],
      classOf[traversal.universe.Select],
      classOf[traversal.universe.Assign],
      classOf[traversal.universe.Match],
      classOf[traversal.universe.Ident],
      classOf[traversal.universe.Apply],
      classOf[traversal.universe.ApplyToImplicitArgs],
      classOf[traversal.universe.ApplyImplicitView]
    )

    validation should be (true)
  }

  it should "recover gracefully" in {
    val traversal = new TestingTraversal {
      import universe._

      val step = optimize {
        case t : Tree =>
      }
    }

    traversal.classes should be (None)
  }

}
