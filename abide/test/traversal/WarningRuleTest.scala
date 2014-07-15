package scala.tools.abide.test.traversal

import scala.tools.abide._
import scala.tools.abide.test._
import scala.tools.abide.traversal._

class WarningRuleTest extends AbideTest {

  val context = new Context(global)
  import global._

  object warningRule extends {
    val context : WarningRuleTest.this.context.type = WarningRuleTest.this.context
  } with WarningRule {
    import context.universe._

    val name = "warning-rule"

    case class Warning(vd : ValDef) extends RuleWarning {
      val pos = vd.pos
      val message = "haha failed!"
    }

    val step = optimize {
      case defDef : DefDef if !defDef.symbol.isSynthetic && !defDef.symbol.owner.isSynthetic =>
        defDef.symbol.overrides.foreach { overriden =>
          (defDef.vparamss.flatten zip overriden.asMethod.paramLists.flatten).foreach { case (vd, o) =>
            if (vd.symbol.name != o.name) {
              nok(Warning(vd))
            }
          }
        }
    }
  }

  def warningTraverse(tree : Tree) : Set[ValDef] = {
    var valDefs : Set[ValDef] = Set.empty

    def rec(tree : Tree) : Unit = tree match {
      case defDef : DefDef if !defDef.symbol.isSynthetic && !defDef.symbol.owner.isSynthetic =>
        defDef.symbol.overrides.foreach { overriden =>
          (defDef.vparamss.flatten zip overriden.asMethod.paramLists.flatten).foreach { case (vd, o) =>
            if (vd.symbol.name != o.name) {
              valDefs += vd
            }
          }
        }
        defDef.children.foreach(rec(_))
      case _ => tree.children.foreach(rec(_))
    }

    rec(tree)
    valDefs
  }

  "Warning rule validity" should "be ensured in AddressBook.scala" in {
    val tree = fromFile("traversal/AddressBook.scala")
    global.ask { () => 
      warningRule.traverse(tree.asInstanceOf[warningRule.universe.Tree])
      val ruleResult = warningRule.result.warnings.map(_.vd).toSet
      val recResult = warningTraverse(tree)

      ruleResult should be (recResult)
    }
  }

  it should "be ensured in SimpleInterpreter.scala" in {
    val tree = fromFile("traversal/SimpleInterpreter.scala")
    global.ask { () => 
      warningRule.traverse(tree.asInstanceOf[warningRule.universe.Tree])
      val ruleResult = warningRule.result.warnings.map(_.vd).toSet
      val recResult = warningTraverse(tree)

      ruleResult should be (recResult)
    }
  }
}
