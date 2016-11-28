package com.lightbend.abide.extra

import scala.tools.abide.traversal.TraversalTest

class InstanceOfUsedTest extends TraversalTest {

  val rule = new InstanceOfUsed(context)

  "usages of asInstanceOf[T]" should "give a warning" in {
    val tree = fromString("""
      class Test {
        val a = 1.asInstanceOf[Any]
      }""")

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  "usages of isInstanceOf[T]" should "give a warning" in {
    val tree = fromString("""
      class Test {
        val a = 1.isInstanceOf[Any]
      }""")

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

}
