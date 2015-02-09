package scala.tools.abide.traversal

import scala.tools.abide._
import scala.tools.abide.traversal._

class ScopingRuleTest extends TraversalTest {

  object rule extends {
    val context: ScopingRuleTest.this.context.type = ScopingRuleTest.this.context
  } with ScopingRule {
    import context.universe._

    val name = "scoping-rule"

    case class Warning(vd: ValDef, sym: Symbol) extends RuleWarning {
      val pos = vd.pos
      val message = "haha failed!"
    }

    val step = optimize {
      case vd: ValDef if vd.name.toString.trim == "bob" =>
        nok(Warning(vd, state.lookup(TermName("titi"), _ => true).symbol))
    }
  }

  "Scoping traversal" should "work with normal imports" in {
    val tree = fromString("""
      object Toto {
        val titi = 1
        private val titi2 = 2
      }
      class Titi {
        import Toto._
        val tutu = titi // use it or import gets removed!
        val bob = 1
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "work with companion imports" in {
    val tree = fromString("""
      package test
      object Titi {
        val titi2 = 1
        private val titi = 2
      }
      class Titi {
        import Titi._
        val bob = 1
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "work with parent elements" in {
    val tree = fromString("""
      class Titi1 {
        val titi2 = 1
        private val titi = 2
      }
      class Titi2 extends Titi1 {
        val bob = 1
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "work in blocks" in {
    val tree = fromString("""
      class Titi {
        def test = {
          val titi = 1
          val bob = 2
          titi
        }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "follow block scoping rules I" in {
    val tree = fromString("""
      class Titi {
        val titi = 2

        def test = {
          val titi = 1
          val bob = 2
          titi
        }
      }
    """)

    global.ask { () =>
      val warnings = apply(rule)(tree)
      assert(warnings.size == 1 && warnings.exists(w => w.sym.fullName == "Titi.titi" && w.sym.owner.isMethod))
    }
  }

  it should "follow block scoping rules II" in {
    val tree = fromString("""
      class Titi {
        val titi = 2

        def test = {
          val bob = 2
          val titi = 1
          titi
        }
      }
    """)

    global.ask { () =>
      val warnings = apply(rule)(tree)
      assert(warnings.size == 1 && warnings.exists(w => w.sym.fullName == "Titi.titi" && w.sym.owner.isClass))
    }
  }

}
