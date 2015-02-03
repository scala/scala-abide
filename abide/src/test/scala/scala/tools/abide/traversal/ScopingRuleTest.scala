package scala.tools.abide.traversal

import scala.tools.abide._
import scala.tools.abide.traversal._

class ScopingRuleTest extends AbideTest {
  val context = new Context(global)
  import global._

  object scopingRule extends {
    val context: ScopingRuleTest.this.context.type = ScopingRuleTest.this.context
  } with ScopingRule {
    import context.universe._

    val name = "scoping-rule"

    case class Warning(vd: ValDef) extends RuleWarning {
      val pos = vd.pos
      val message = "haha failed!"
    }

    val step = optimize {
      case vd: ValDef =>

    }
  }

  "ScopingRule" should "work" in {
    val tree = fromString("""
      object Toto {
        val a = 1
        private val b = 2
      }
      class Titi {
        import Toto._
      }
    """)
    global.ask { () =>
      scopingRule.traverse(tree.asInstanceOf[scopingRule.universe.Tree])
      val ruleResult = scopingRule.result.warnings
    }
  }

}
