package scala.tools.abide
package tests.rules

class ValInsteadOfVarTest extends tests.AbideTest {

  object localAnalyzer extends {
    val global : ValInsteadOfVarTest.this.global.type = ValInsteadOfVarTest.this.global
  } with Analyzer

  object localRule extends scala.tools.abide.rules.LocalValInsteadOfVar(localAnalyzer)
  localAnalyzer.register(localRule)

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
      val warnings = localAnalyzer(tree)
      warnings.map(_.toString).sorted should be (List("variable b", "variable c"))
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
      val warnings = localAnalyzer(tree)
      warnings.isEmpty should be (true)
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
      val warnings = localAnalyzer(tree)
      warnings.isEmpty should be (true)
    }
  }

  it should "not show up in member analysis" in {
    val tree = fromString("""
      class Toto {
        private var a = 0
      }
    """)

    global.ask { () =>
      val warnings = localAnalyzer(tree)
      warnings.isEmpty should be (true)
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
      val warnings = localAnalyzer(tree2)
      warnings.isEmpty should be (true)
    }
  }

  object memberAnalyzer extends {
    val global : ValInsteadOfVarTest.this.global.type = ValInsteadOfVarTest.this.global
  } with Analyzer

  object memberRule extends scala.tools.abide.rules.MemberValInsteadOfVar(memberAnalyzer)
  memberAnalyzer.register(memberRule)

  "Member local vars" should "be vals when not assigned" in {
    val tree = fromString("""
      class Toto {
        private var a = 0
        def test(i: Int) : Int = a + i
      }
    """)

    global.ask { () =>
      val warnings = memberAnalyzer(tree)
      warnings.map(_.toString).sorted should be (List("variable a"))
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
      val warnings = memberAnalyzer(tree)
      warnings.isEmpty should be (true)
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
      val warnings = memberAnalyzer(tree)
      warnings.isEmpty should be (true)
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
      val warnings = memberAnalyzer(tree)
      warnings.isEmpty should be (true)
    }
  }

}
