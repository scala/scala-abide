package scala.tools.abide
package tests.rules

class PublicMutableTest extends tests.AbideTest {

  object analyzer extends {
    val global : PublicMutableTest.this.global.type = PublicMutableTest.this.global
  } with Analyzer

  object rule extends scala.tools.abide.rules.PublicMutable(analyzer)
  analyzer.register(rule)

  "Immutability" should "be guaranteed for public vals" in {
    val tree = fromString("""
      class Toto {
        val toto : List[Int] = Nil
      }
    """)

    global.ask { () => analyzer(tree).isEmpty should be (true) }
  }

  it should "not matter in public defs" in {
    val tree = fromString("""
      class Mut { var a : Int = 0 }
      class Toto {
        def toto : Mut = new Mut
      }
    """)

    global.ask { () => analyzer(tree).isEmpty should be (true) }
  }

  it should "not matter in vars" in {
    val tree = fromString("""
      class Mut { var a : Int = 0 }
      class Toto {
        var toto : Mut = new Mut
      }
    """)

    global.ask { () => analyzer(tree).isEmpty should be (true) }
  }

  it should "not matter in private vals" in {
    val tree = fromString("""
      class Mut { var a : Int = 0 }
      class Toto {
        private val toto : Mut = new Mut
      }
    """)

    global.ask { () => analyzer(tree).isEmpty should be (true) }
  }

  "Mutability" should "be warned about in public vals" in {
    val tree = fromString("""
      class Mut { var a : Int = 0 }
      class Toto {
        val mut : Mut = new Mut
      }
    """)

    global.ask { () => analyzer(tree).map(_.toString).sorted should be (List("value mut")) }
  }
}
