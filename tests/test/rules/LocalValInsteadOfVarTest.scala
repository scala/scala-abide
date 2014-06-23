package scala.tools.abide
package rules

class LocalValInsteadOfVarTest extends AnalysisTest {
  import scala.tools.abide.traversal._

  analyzer.enableOnly("local-val-instead-of-var")

  "Definition local vars" should "be vals when not assigned" in {
    val tree = fromString("""
      class Toto {
        def test(a : Int) = {
          var b : Int = 2
          var c = 2
          a + b
        }
      }
    """)

    global.ask { () =>
      val syms = analyzer(tree).map(_.asInstanceOf[LocalValInsteadOfVar#Warning].tree.symbol.toString)
      syms.sorted should be (List("variable b", "variable c"))
    }
  }

  it should "be vars when assigned" in {
    val tree = fromString("""
      class Toto {
        def test(a : Int) = {
          var b = 0
          b = a
          b
        }
      }
    """)

    global.ask { () =>
      analyzer(tree).isEmpty should be (true)
    }
  }

  it should "be vars when assigned through special op" in {
    val tree = fromString("""
      class Toto {
        def test(a : Int) = {
          var b = 0
          b += a
          b
        }
      }
    """)

    global.ask { () =>
      analyzer(tree).isEmpty should be (true)
    }
  }

  it should "not show up in member analysis" in {
    val tree = fromString("""
      class Toto {
        private var a = 0
      }
    """)

    global.ask { () =>
      analyzer(tree).isEmpty should be (true)
    }

    val tree2 = fromString("""
      class Toto {
        private var a = 0
        def test() {
          a = 2
        }
      }
    """)

    global.ask { () =>
      analyzer(tree2).isEmpty should be (true)
    }
  }


}
