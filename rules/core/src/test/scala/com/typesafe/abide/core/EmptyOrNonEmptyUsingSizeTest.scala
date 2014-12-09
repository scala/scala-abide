package com.typesafe.abide.core

import scala.tools.abide.traversal.TraversalTest

class EmptyOrNonEmptyUsingSizeTest extends TraversalTest {

  val rule = new EmptyOrNonEmptyUsingSize(context)

  "Using size or length == 0 on Traversables" should "give a warning" in {
    val tree = fromString("""
      class Test {
        List().size == 0
        List().length == 0
        Seq().size == 0
        Seq().length == 0
        Set().size == 0
        Map().size == 0
      } """)

    global.ask { () => apply(rule)(tree).size should be(6) }
  }

  "Using size or length != 0 on Traversables" should "give a warning" in {
    val tree = fromString("""
      class Test {
        List().size != 0
        List().length != 0
        Seq().size != 0
        Seq().length != 0
        Set().size != 0
        Map().size != 0
      } """)

    global.ask { () => apply(rule)(tree).size should be(6) }
  }

  "Using size or length == 0 on objects not subclasses to Traversable" should "not give a warning" in {
    val tree = fromString("""
      class Test {
        class Something {
          def size = 0
        }

        val s = new Something
        s.size == 0
      }""")

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

}
