package com.typesafe.abide.core

import scala.tools.abide.traversal._
import com.typesafe.abide.core._

class NullaryUnitTest extends TraversalTest {

  val rule = new NullaryUnit(context)

  "Nullary methods" should "be valid when their return type is not unit" in {
    val tree = fromString("""
      class Test {
        def nullary1 = 1
        def nullary2 = "2"
        def nullary3 = '3'
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  it should "not be valid when they return unit" in {
    val tree = fromString("""
      class Test {
        def unit = ()
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not be valid when their return type is unit" in {
    val tree = fromString("""
      class Test {
        def nullary: Unit = 23
        def notNullary: Int = 23
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be valid if they have type parameters" in {
    val tree = fromString("""
      class Test {
        def nullaryP[T]: Unit = ()
        def nullaryP2[T, U] = println("hello")
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  it should "not be valid for each nullary method returning unit" in {
    val tree = fromString("""
      class Test {
        private def nullaryUnit = ()
        def nullaryNotUnit = 23
        def nonNullaryUnit() = ()
        def paramNullary[T] = ()
        var x = 1
        def moreComplicatedNullary = {
          println("start")
          x = x * 2
        }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(2) }
  }
}
