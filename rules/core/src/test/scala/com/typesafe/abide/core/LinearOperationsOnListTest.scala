package com.typesafe.abide.core

import scala.tools.abide.traversal._
import com.typesafe.abide.core._

class LinearOperationsOnListTest extends TraversalTest {

  val rule = new LinearOperationsOnList(context)

  "Vector" should "not report a warning when calling Vector.:+" in {
    val tree = fromString("""
      class Toto {
        Vector(1,2,3) :+ 2
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  "List" should "not report a warning when prepending" in {
    val tree = fromString("""
          class Toto {
            2 +: List(1,2,3) 
            2 :: List(1,2,3)
          }
        """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  it should "not report a warning when prepending a List to another List" in {
    val tree = fromString("""
          class Toto {
            List(1) ::: List(2,3)
          }
        """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  it should "report a warning when calling List.:+" in {
    val tree = fromString("""
        class Toto {
          List(1,2,3) :+ 2
        }
      """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "report a warning when calling List.length" in {
    val tree = fromString("""
          class Toto {
            List(1,2,3).length
          }
        """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "report a warning when calling List.lengthCompare" in {
    val tree = fromString("""
          class Toto {
            List(1,2,3).lengthCompare(2)
          }
        """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "report a warning when calling List.size" in {
    val tree = fromString("""
          class Toto {
            List(1,2,3).size
          }
        """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "report a warning when calling List.last" in {
    val tree = fromString("""
          class Toto {
            val xs = List(1,2,3)
            xs.last
          }
        """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "report a warning when calling List.lastOption" in {
    val tree = fromString("""
          class Toto {
            val xs = List(1,2,3)
            xs.lastOption
          }
        """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "report a warning when calling List.init" in {
    val tree = fromString("""
          class Toto {
            val xs = List(1,2,3)
            xs.init
          }
        """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "report a warning when calling List.apply" in {
    val tree = fromString("""
          class Toto {
            val xs = List(1,2,3)
            xs(0)
          }
        """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "report a warning when calling List.updated" in {
    val tree = fromString("""
          class Toto {
            val xs = List(1,2,3)
            xs.updated(0, 2)
          }
        """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not report a warning when calling List.head through an alias" in {
    val tree = fromString("""
          class Toto {
            val xs = List(1,2,3)
            import xs.{ head => firstElement }
            firstElement
          }
        """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  it should "report a warning when calling List.last through an alias" in {
    val tree = fromString("""
          class Toto {
            val xs = List(1,2,3)
            import xs.{ last => lastElement }
            lastElement
          }
        """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }
}