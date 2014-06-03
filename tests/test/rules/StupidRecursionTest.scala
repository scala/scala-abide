package scala.tools.abide
package rules

class StupidRecursionTest extends AbideTest {

  val analyzer = new DefaultAnalyzer(global).enableOnly("stupid-recursion")

  "Definitions without parameters" should "not be stupidly defined as themselves" in {
    val tree = fromString("""
      class Toto {
        def test: Int = test
      }
    """)

    global.ask { () => analyzer(tree).size should be (1) }
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

    global.ask { () => analyzer(tree).size should be (1) }
  }
}
