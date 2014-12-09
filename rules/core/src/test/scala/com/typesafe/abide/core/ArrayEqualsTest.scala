package com.typesafe.abide.core

import scala.tools.abide.traversal.TraversalTest

class ArrayEqualsTest extends TraversalTest {

  val rule = new ArrayEquals(context)

  "Using equals with arrays" should "give a warning" in {
    val tree = fromString("""
      class Test {
        Array[Int](1) == Array[Int](1)

        val a1 = Array[Int](0)
        val a2 = Array[Int](0)
        a1 == a2
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(2) }
  }

}
