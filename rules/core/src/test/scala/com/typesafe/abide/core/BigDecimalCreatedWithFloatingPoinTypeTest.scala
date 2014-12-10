package com.typesafe.abide.core

import scala.tools.abide.traversal.TraversalTest

class BigDecimalCreatedWithFloatingPoinTypeTest extends TraversalTest {

  val rule = new BigDecimalCreatedWithFloatingPointType(context)

  "Creating a big decimal from a float" should "give a warning" in {
    val tree = fromString("""
      class Test {
        val b1 = BigDecimal(0.1F)
      }""")

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  "Creating a big decimal from a double" should "give a warning" in {
    val tree = fromString("""
      class Test {
        val b1 = BigDecimal(0.1)
        val b2 = new java.math.BigDecimal(0.1)
      }""")

    global.ask { () => apply(rule)(tree).size should be(2) }
  }

  "Creating a big decimal from an int" should "not give a warning" in {
    val tree = fromString("""
      class Test {
        val b1 = BigDecimal(1)
        val b2 = new java.math.BigDecimal(1)
      }""")

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  "Creating other objects with a float" should "not give a warning" in {
    val tree = fromString("""
      case class FloatBox(f: Float)
      class Test {
        val b1 = FloatBox(1.0F)
        val b2 = new FloatBox(1.0F)
      }""")

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

}
