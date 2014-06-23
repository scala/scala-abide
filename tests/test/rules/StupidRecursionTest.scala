package scala.tools.abide
package rules

class StupidRecursionTest extends AnalysisTest {
  import scala.tools.abide.traversal._

  analyzer.enableOnly("stupid-recursion")

  "Definitions without parameters" should "not be stupidly defined as themselves" in {
    val tree = fromString("""
      class Toto {
        def test: Int = test
      }
    """)

    global.ask { () =>
      val syms = analyzer(tree).map(_.asInstanceOf[StupidRecursion#Warning].tree.symbol.toString)
      syms.sorted should be (List("method test"))
    }
  }

  it should "should also be identified in local methods" in {
    val tree = fromString("""
      class Toto {
        def test : Int = {
          def rec : Int = rec
          rec
        }
      }
    """)

    global.ask { () =>
      val syms = analyzer(tree).map(_.asInstanceOf[StupidRecursion#Warning].tree.symbol.toString)
      syms.sorted should be (List("method rec"))
    }
  }

  it should "not be identified in non-direct children" in {
    val tree = fromString("""
      class Toto(val a : Int) {
        trait Titi {
          val a : Int
        }

        val titi = new Titi {
          val a = Toto.this.a
        }
      }
    """)

    global.ask { () => analyzer(tree).isEmpty should be (true) }
  }
}
