package com.typesafe.abide.core.test

import scala.tools.abide.test.traversal._
import com.typesafe.abide.core._

class PublicMutableTest extends TraversalTest {
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

  it should "not matter in private[this] members" in {
    val tree = fromString("""
      class Toto {
        private[this] val sb = new StringBuilder
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be (true) }
  }

  it should "not matter for self-member" in {
    val tree = fromString("""
      trait Toto extends Mutable { self =>
        val i = 1
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
