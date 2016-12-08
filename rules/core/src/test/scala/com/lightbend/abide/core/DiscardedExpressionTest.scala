package com.lightbend.abide.core

import scala.tools.abide.traversal._

class DiscardedExpressionTest extends TraversalTest {

  val rule = new DiscardedExpression(context)

  "Discarded expressions" should "be discovered" in {
    val tree = fromString("""
      package foo {
         class Bar()
      }

      class Toto {
        def x() = 42
        def y() = new foo.Bar()
        def z() = {
          x()
          y()
          "ok"
        }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(2) }
  }

  it should "be discovered when type aliased" in {
    val tree = fromString("""
      package foo {
         class Bar()
      }

      class Toto {
        type fb = foo.Bar
        def x(): fb = new foo.Bar()
        def z() = {
          x()
          "ok"
        }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be (1) }
  }

  "Used expressions" should "be valid" in {
    val tree = fromString("""
      class Toto {
        def x() = 42
        def y() = {
          x()
        }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  "Discarded expressions not on the warning list" should "be valid" in {
    val tree = fromString("""
      package baz {
         class Bar()
      }

      class Toto {
        def y() = new baz.Bar()
        def z() = {
          y()
          "ok"
        }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }
}
