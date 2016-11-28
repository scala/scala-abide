package com.lightbend.abide.core

import scala.tools.abide.traversal.TraversalTest

class EmptyOrNonEmptyUsingSizeTest extends TraversalTest {

  val rule = new EmptyOrNonEmptyUsingSize(context)

  "Using size == 0 on List" should "give a warning" in {
    val tree = fromString("""
      class Test {
        List().size == 0
      } """)

    global.ask { () => apply(rule)(tree).nonEmpty should be(true) }
  }

  "Using size != 0 on List" should "give a warning" in {
    val tree = fromString("""
      class Test {
        List().size != 0
      } """)

    global.ask { () => apply(rule)(tree).nonEmpty should be(true) }
  }

  "Using length == 0 on List" should "give a warning" in {
    val tree = fromString("""
      class Test {
        List().length == 0
      } """)

    global.ask { () => apply(rule)(tree).nonEmpty should be(true) }
  }

  "Using length != 0 on List" should "give a warning" in {
    val tree = fromString("""
      class Test {
        List().length != 0
      } """)

    global.ask { () => apply(rule)(tree).nonEmpty should be(true) }
  }

  "Using size == 0 on Seq" should "give a warning" in {
    val tree = fromString("""
      class Test {
        Seq().size == 0
      } """)

    global.ask { () => apply(rule)(tree).nonEmpty should be(true) }
  }

  "Using size != 0 on Seq" should "give a warning" in {
    val tree = fromString("""
      class Test {
        Seq().size != 0
      } """)

    global.ask { () => apply(rule)(tree).nonEmpty should be(true) }
  }

  "Using length == 0 on Seq" should "give a warning" in {
    val tree = fromString("""
      class Test {
        Seq().length == 0
      } """)

    global.ask { () => apply(rule)(tree).nonEmpty should be(true) }
  }

  "Using length != 0 on Seq" should "give a warning" in {
    val tree = fromString("""
      class Test {
        Seq().length != 0
      } """)

    global.ask { () => apply(rule)(tree).nonEmpty should be(true) }
  }

  "Using size == 0 on Set" should "give a warning" in {
    val tree = fromString("""
      class Test {
        Set().size == 0
      } """)

    global.ask { () => apply(rule)(tree).nonEmpty should be(true) }
  }

  "Using size != 0 on Set" should "give a warning" in {
    val tree = fromString("""
      class Test {
        Set().size != 0
      } """)

    global.ask { () => apply(rule)(tree).nonEmpty should be(true) }
  }

  "Using size == 0 on Map" should "give a warning" in {
    val tree = fromString("""
      class Test {
        Map().size == 0
      } """)

    global.ask { () => apply(rule)(tree).nonEmpty should be(true) }
  }

  "Using size != 0 on Map" should "give a warning" in {
    val tree = fromString("""
      class Test {
        Map().size != 0
      } """)

    global.ask { () => apply(rule)(tree).nonEmpty should be(true) }
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
