package scala.tools.abide.test.rules

import scala.tools.abide.test._
import com.typesafe.abide.sample._

class LocalValInsteadOfVarTest extends AnalysisTest {

  val rule = new LocalValInsteadOfVar(context)

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
      val syms = apply(rule)(tree).map(_.tree.symbol.toString)
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
      apply(rule)(tree).isEmpty should be (true)
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
      apply(rule)(tree).isEmpty should be (true)
    }
  }

  it should "not show up in member analysis" in {
    val tree = fromString("""
      class Toto {
        private var a = 0
      }
    """)

    global.ask { () =>
      apply(rule)(tree).isEmpty should be (true)
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
      apply(rule)(tree2).isEmpty should be (true)
    }
  }


}
