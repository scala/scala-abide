package com.lightbend.abide.core

import scala.tools.abide.traversal._
import com.lightbend.abide.core._

class NullaryOverrideTest extends TraversalTest {

  val rule = new NullaryOverride(context)

  "Non-nullary methods" should "be valid when not overriding anything" in {
    val tree = fromString("""
      class Test {
        def nonNullary() = 123
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be valid when overriding other non-nullary methods" in {
    val tree = fromString("""
      class Parent {
        def nonNullary() = 123
      }
      class Child extends Parent {
        override def nonNullary() = 456
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "not be valid when overriding nullary methods" in {
    val tree = fromString("""
      class Parent {
        def nullary = 123
      }
      class Child extends Parent {
        override def nullary() = 456
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be invalid only once when overriding an overridden nullary method" in {
    val tree = fromString("""
      class A {
        def nullary = 123
      }
      class B extends A {
        override def nullary = 456
      }
      class C extends B {
        override def nullary() = 789
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be invalid only once when overriding multiple nullary methods" in {
    val tree = fromString("""
      trait A {
        def nullary = 123
      }
      trait B {
        def nullary = 456
      }
      trait C {
        def nullary() = 789
      }
      class D extends A with B with C {
        override def nullary() = 0
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  "Nullary methods" should "be valid when overriding non-nullary methods" in {
    val tree = fromString("""
      class Parent {
        def nonNullary() = 123
      }
      class Child extends Parent {
        override def nonNullary = 456
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be valid when overriding other nullary methods" in {
    val tree = fromString("""
      class Parent {
        def nonNullary = 123
      }
      class Child extends Parent {
        override def nonNullary = 456
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }
}
