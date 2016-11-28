package com.lightbend.abide.core

import scala.tools.abide.traversal._
import com.lightbend.abide.core._

class InaccessibleTest extends scala.tools.abide.traversal.TraversalTest {

  val rule = new Inaccessible(context)

  "Methods with private types" should "not be valid if those types are in their arguments" in {
    val tree = fromString("""
      class Test {
        private class T
        def method(x: T) = 2
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not be valid if those types are in their type bounds" in {
    val tree = fromString("""
      package test {
        private[test] trait PrivateTrait

        trait Trait {
          def method[T <: PrivateTrait](x: T): T = x
        }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not be valid if those types are in their arguments' type parameters" in {
    val tree = fromString("""
      package test {
        private[test] trait PrivateTrait

        trait Trait {
          def method(x: Map[Int, Set[PrivateTrait]]) = 5
        }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be valid if it is final" in {
    val tree = fromString("""
      class Test {
        private trait Trait
        final def method1(x: Trait) = 2
        final def method2[T <: Trait](x: T) = x
        final def method3(x: Map[Int, Set[Trait]]) = 5
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  "Methods without private types" should "be valid" in {
    val tree = fromString("""
      class Test {
        trait T
        def method(x: T) = 2
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }
}
