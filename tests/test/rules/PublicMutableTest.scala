package scala.tools.abide.test.rules

import scala.tools.abide.test._
import com.typesafe.abide.sample._

class PublicMutableTest extends AnalysisTest {
  import scala.tools.abide.traversal._

  val rule = new PublicMutable(context)

  "Immutability" should "be guaranteed for public vals" in {
    val tree = fromString("""
      class Toto {
        val toto : List[Int] = Nil
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be (true) }
  }

  it should "not matter in public defs" in {
    val tree = fromString("""
      class Mut { private var a : Int = 0 }
      class Toto {
        def toto : Mut = new Mut
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be (true) }
  }

  it should "not matter in private vals" in {
    val tree = fromString("""
      class Mut { private var a : Int = 0 }
      class Toto {
        private val toto : Mut = new Mut
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be (true) }
  }

  "Mutability" should "be warned about in public vals" in {
    val tree = fromString("""
      class Mut { var a : Int = 0 }
      class Toto {
        val mut : Mut = new Mut
      }
    """)

    global.ask { () =>
      val syms = apply(rule)(tree).map(_.tree.symbol.toString)
      syms.sorted should be (List("value mut", "variable a"))
    }
  }

  it should "be warned about in public vars" in {
    val tree = fromString("""
      class Toto {
        var a = 0
      }
    """)

    global.ask { () =>
      val syms = apply(rule)(tree).map(_.tree.symbol.toString)
      syms.sorted should be (List("variable a"))
    }
  }

  it should "be warned about in public mutable vars" in {
    val tree = fromString("""
      class Mut { private var a : Int = 0 }
      class Toto {
        var toto : Mut = new Mut
      }
    """)

    global.ask { () =>
      val syms = apply(rule)(tree).map(_.tree.symbol.toString)
      syms.sorted should be (List("variable toto"))
    }
  }

}
