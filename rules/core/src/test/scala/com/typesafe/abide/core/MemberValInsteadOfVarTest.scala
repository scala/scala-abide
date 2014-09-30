package scala.tools.abide.core

import scala.tools.abide.traversal._
import com.typesafe.abide.core._

class MemberValInsteadOfVarTest extends TraversalTest {

  val rule = new MemberValInsteadOfVar(context)

  "Member local vars" should "be vals when not assigned" in {
    val tree = fromString("""
      class Toto {
        private var a = 0
        def test(i: Int) : Int = a + i
      }
    """)

    global.ask { () =>
      val syms = apply(rule)(tree).map(_.tree.symbol.toString)
      syms.sorted should be(List("variable a"))
    }
  }

  it should "be vars when assigned" in {
    val tree = fromString("""
      class Toto {
        private var a = 0
        def test(i : Int) : Unit = { a = i }
        def test2(i : Int) : Int = a + i
      }
    """)

    global.ask { () =>
      apply(rule)(tree).isEmpty should be(true)
    }
  }

  it should "be vars when assignment predates declaration" in {
    val tree = fromString("""
      class Toto {
        def test(i : Int) : Unit = { a = i }
        private var a = 0
      }
    """)

    global.ask { () =>
      apply(rule)(tree).isEmpty should be(true)
    }
  }

  it should "be ignored when var isn't private" in {
    val tree = fromString("""
      class Toto {
        protected var a = 0
        var b = 0
      }
    """)

    global.ask { () =>
      apply(rule)(tree).isEmpty should be(true)
    }
  }

}
