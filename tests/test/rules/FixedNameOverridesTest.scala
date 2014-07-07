package scala.tools.abide.test.rules

import scala.tools.abide.test._
import com.typesafe.abide.sample._

class FixedNameOverridesTest extends AnalysisTest {

  val rule = new FixedNameOverrides(context)

  "Methods overrides" should "be valid when not renamed" in {
    val tree = fromString("""
      class Toto {
        def test(a : Int) = a + 1
      }
      class Titi extends Toto {
        override def test(a : Int) = a + 2
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be (true) }
  }

  it should "not be valid when renamed" in {
    val tree = fromString("""
      class Toto {
        def test(a : Int) = a + 1
      }
      class Titi extends Toto {
        override def test(b : Int) = b + 2
      }
    """)

    global.ask { () => apply(rule)(tree).size should be (1) }
  }

  it should "not be valid when renamed abstracts" in {
    val tree = fromString("""
      trait Toto {
        def test(a : Int, b : String) : Int
      }
      class Titi extends Toto {
        def test(a : Int, c : String) = a + 1
      }
    """)

    global.ask { () => apply(rule)(tree).size should be (1) }
  }

  it should "not be valid for each argument" in {
    val tree = fromString("""
      trait Toto {
        def test(a : Int, b : String) : Int
      }
      class Titi extends Toto {
        def test(e : Int, c : String) = e + 1
      }
    """)

    global.ask { () => apply(rule)(tree).size should be (2) }
  }

  it should "not be valid for each override" in {
    val tree = fromString("""
      trait Toto {
        def test(a : Int, b : String) : Int
      }
      class Titi extends Toto {
        def test(a : Int, c : String) = a + 1
      }
      class Tata extends Toto {
        def test(e : Int, b : String) = e
      }
    """)

    global.ask { () => apply(rule)(tree).size should be (2) }
  }

  it should "be valid in case-class definitions" in {
    val tree = fromString("""
      case class Person(asdbfds : Int)
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be (true) }
  }

  it should "be valid in partial function desugaring" in {
    val tree = fromString("""
      trait Toto {
        val a : PartialFunction[String, Unit] = {
          case "abc" => ()
          case _ =>
        }
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be (true) }
  }

}
