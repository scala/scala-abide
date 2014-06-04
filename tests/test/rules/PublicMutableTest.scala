package scala.tools.abide
package rules

class PublicMutableTest extends AbideTest {

  val analyzer = new DefaultAnalyzer(global).enableOnly("public-mutable-fields")

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
      class Mut { private var a : Int = 0 }
      class Toto {
        def toto : Mut = new Mut
      }
    """)

    global.ask { () => analyzer(tree).isEmpty should be (true) }
  }

  it should "not matter in private vals" in {
    val tree = fromString("""
      class Mut { private var a : Int = 0 }
      class Toto {
        private val toto : Mut = new Mut
      }
    """)

    global.ask { () => analyzer(tree).isEmpty should be (true) }
  }

  "Mutability" should "be warned about in public vals" in {
    val tree = fromString("""
      class Mut { private var a : Int = 0 }
      class Toto {
        val mut : Mut = new Mut
      }
    """)

    global.ask { () => analyzer(tree).map(_.toString).size should be (1) }
  }

  it should "be warned about in public vars" in {
    val tree = fromString("""
      class Toto {
        var a = 0
      }
    """)

    global.ask { () => analyzer(tree).size should be (1) }
  }

  it should "be warned about in public mutable vars" in {
    val tree = fromString("""
      class Mut { private var a : Int = 0 }
      class Toto {
        var toto : Mut = new Mut
      }
    """)

    global.ask { () => analyzer(tree).size should be (1) }
  }

}
