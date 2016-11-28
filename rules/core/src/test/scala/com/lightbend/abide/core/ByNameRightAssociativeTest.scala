package com.lightbend.abide.core

import scala.tools.abide.traversal._
import com.lightbend.abide.core._

class ByNameRightAssociativeTest extends TraversalTest {

  val rule = new ByNameRightAssociative(context)

  "Right-associative operators" should "be valid when not using by-name parameters" in {
    val tree = fromString("""
      class Test {
        def m_:(x: Int) = ()
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "not be valid when using by-name parameters" in {
    val tree = fromString("""
      class Test {
        def m_:(x: => Int) = ()
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not be valid for each method with by-name parameters" in {
    val tree = fromString("""
      class Test {
        def m_:(x: => Int) = ()
        def m2_:(x: => Int, y: => Char) = ()
        private def m3_:(x: Int, y: => Char) = ()
        private def m4_:(x: Int) = ()
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(3) }
  }

  it should "not be valid with by-name parameters in the first argument list" in {
    val tree = fromString("""
      class Test {
        def op3_:(x: Any, y: => Any)(a: Any) = ()
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be valid with by-name parameters in the second argument list" in {
    val tree = fromString("""
      class Test {
        def op6_:(x: Any)(a: => Any) = ()
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  "Left associative operators" should "be valid when using by-name parameters" in {
    val tree = fromString("""
      class Test {
        def m(x: => Int) = ()
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be valid when not using by-name parameters" in {
    val tree = fromString("""
      class Test {
        def m(x: => Int) = ()
        def m2(x: => Int, y: => Char) = ()
        def m3(x: Int, y: => Char) = ()
        def m4(x: Int) = ()
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }
}
