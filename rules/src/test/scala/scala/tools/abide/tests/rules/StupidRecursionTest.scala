package scala.tools.abide
package tests.rules

class StupidRecursionTest extends tests.AbideTest {
  object analyzer extends {
    val global : StupidRecursionTest.this.global.type = StupidRecursionTest.this.global
  } with Analyzer

  analyzer.register(new scala.tools.abide.rules.StupidRecursion(analyzer))

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
