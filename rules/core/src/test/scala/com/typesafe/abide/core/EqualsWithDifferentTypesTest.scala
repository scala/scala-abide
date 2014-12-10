package com.typesafe.abide.core

import scala.tools.abide.traversal.TraversalTest

class EqualsWithDifferentTypesTest extends TraversalTest {

  val rule = new EqualsWithDifferentTypes(context)

  "Using equals with different types" should "give a warning" in {
    val tree = fromString("""
      class Test {
        "a" == 1
        Set() == List()
      } """)

    global.ask { () => apply(rule)(tree).size should be(2) }
  }

  "Using equals with different types that will be implicitly converted" should "not give a warning" in {
    val tree = fromString("""
      class Test {
        1.0 == 1
        1.0F == 1.0
      } """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  "Using equals with the same type" should "not give a warning" in {
    val tree = fromString("""
      class Test {
        1 == 1
        "a" == "b"
        List() == List()
      } """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

}
