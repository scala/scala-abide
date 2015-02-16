package com.typesafe.abide.core

import scala.tools.abide.traversal._
import com.typesafe.abide.core._

class DelayedInitSelectTest extends TraversalTest {

  val rule = new DelayedInitSelect(context)

  "Selection from DelayedInit" should "be valid in the extended object" in {
    val tree = fromString("""
      object O extends App {
        val vall = ""

        println(vall)
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be valid in constructions called in the extended object" in {
    val tree = fromString("""
      object O extends App {
        val vall = ""

        new { println(vall) }
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "not be valid from a different object" in {
    val tree = fromString("""
      object O extends App {
        val vall = ""
      }

      object Client {
        println(O.vall)
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not be valid from a different object when imported" in {
    val tree = fromString("""
      object O extends App {
        val vall = ""
      }

      object Client {
        import O.vall
        println(vall)
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be valid when selecting a lazy value" in {
    val tree = fromString("""
      object O extends App {
        lazy val vall = ""
      }

      object Client {
        println(O.vall)
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be valid when selecting a method" in {
    val tree = fromString("""
      object O extends App {
        def method = ""
      }

      object Client {
        println(O.method)
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be valid when selecting from a superclass other than the DelayedInit" in {
    val tree = fromString("""
      trait T {
        val traitVal = ""
      }

      object O extends App with T {
        def method = ""
      }

      object Client {
        println(O.traitVal)
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }
}
