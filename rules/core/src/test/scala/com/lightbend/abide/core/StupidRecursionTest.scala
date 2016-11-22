package com.lightbend.abide.core

import scala.tools.abide.traversal._
import com.lightbend.abide.core._

class StupidRecursionTest extends TraversalTest {

  val rule = new StupidRecursion(context)

  "Definitions without parameters" should "not be stupidly defined as themselves" in {
    val tree = fromString("""
      class Toto {
        def test: Int = test
      }
    """)

    global.ask { () =>
      val syms = apply(rule)(tree).map(_.tree.symbol.toString)
      syms.sorted should be(List("method test"))
    }
  }

  it should "should also be identified in local methods" in {
    val tree = fromString("""
      class Toto {
        def test : Int = {
          def rec : Int = rec
          rec
        }
      }
    """)

    global.ask { () =>
      val syms = apply(rule)(tree).map(_.tree.symbol.toString)
      syms.sorted should be(List("method rec"))
    }
  }

  it should "not be identified in non-direct children" in {
    val tree = fromString("""
      class Toto(val a : Int) {
        trait Titi {
          val a : Int
        }

        val titi = new Titi {
          val a = Toto.this.a
        }
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }
}
