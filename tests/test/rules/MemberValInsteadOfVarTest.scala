package scala.tools.abide
package rules

class MemberValInsteadOfVarTest extends AbideTest {

  val analyzer = new DefaultAnalyzer(global).enableOnly("member-val-instead-of-var")

  "Member local vars" should "be vals when not assigned" in {
    val tree = fromString("""
      class Toto {
        private var a = 0
        def test(i: Int) : Int = a + i
      }
    """)

    global.ask { () =>
      analyzer(tree).map(_.toString).sorted should be (List("variable a"))
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
      analyzer(tree).isEmpty should be (true)
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
      analyzer(tree).isEmpty should be (true)
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
      analyzer(tree).isEmpty should be (true)
    }
  }

}
