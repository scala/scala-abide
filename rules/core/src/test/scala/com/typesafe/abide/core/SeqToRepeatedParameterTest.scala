package com.typesafe.abide.core

import scala.tools.abide.traversal._

class SeqToRepeatedParameterTest extends TraversalTest {

  val rule = new SeqToRepeatedParameter(context)

  it should "find applications with inferred repeated arguments" in {
    val tree = fromString("""
      import scala.collection.mutable._

      class Foo {
        def foo = {
          val buf = ListBuffer(1, 2)

          Vector(buf)
        }
      }
    """)

    global.ask { () =>
      val syms = apply(rule)(tree).map(_.tree.symbol.toString)
      syms.sorted should be(List("value buf"))
    }
  }

  it should "not find applications with concrete parameter types" in {
    val tree = fromString("""
      import scala.collection.mutable._

      class Foo {
        def foo = {
          val buf = ListBuffer(1, 2)

          bar(buf)
        }

        def bar(vs: Seq[Int]*) = ???
      }
    """)

    global.ask { () =>
      val syms = apply(rule)(tree).map(_.tree.symbol.toString)
      syms.sorted should be(List())
    }
  }
}