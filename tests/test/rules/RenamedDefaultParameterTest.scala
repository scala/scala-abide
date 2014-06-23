package scala.tools.abide
package rules

class RenamedDefaultParameterTest extends AnalysisTest {
  analyzer.enableOnly("renamed-default-parameter")

  "Methods without defaults" should "be valid when not renamed" in {
    val tree = fromString("""
      class Toto {
        def test(a : Int) = a + 1
      }
      class Titi extends Toto {
        override def test(a : Int) = a + 2
      }
    """)

    global.ask { () => analyzer(tree).isEmpty should be (true) }
  }

  it should "be valid when renamed" in {
    val tree = fromString("""
      class Toto {
        def test(a : Int) = a + 1
      }
      class Titi extends Toto {
        override def test(a : Int) = a + 2
      }
    """)

    global.ask { () => analyzer(tree).isEmpty should be (true) }
  }

  "Methods with defaults" should "be valid when not renamed" in {
    val tree = fromString("""
      class Toto {
        def test(x : Int = 1, y : Int = 2) = x + y + 1
      }
      class Titi extends Toto {
        override def test(x : Int = 2, y : Int = 1) = x + y + 2
      }
    """)

    global.ask { () => analyzer(tree).isEmpty should be (true) }
  }

  it should "be valid when renamed to non-existing parameter" in {
    val tree = fromString("""
      class Toto {
        def test(x : Int = 1, y : Int = 2) = x + y + 1
      }
      class Titi extends Toto {
        override def test(x : Int = 2, z : Int = 1) = x + z + 2
      }
    """)

    global.ask { () => analyzer(tree).isEmpty should be (true) }
  }

  it should "be invalid when renamed" in {
    val tree = fromString("""
      class Toto {
        def test(x : Int = 1, y : Int = 2) = x + y + 1
      }
      class Titi extends Toto {
        override def test(y : Int = 2, x : Int = 1) = x + y + 2
      }
    """)

    global.ask { () => analyzer(tree).size should be (2) }
  }
}
