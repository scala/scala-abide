package com.lightbend.abide.core

import scala.tools.abide.traversal._
import com.lightbend.abide.core._

class PrivateShadowTest extends TraversalTest {

  val rule = new PrivateShadow(context)

  "Field selection" should "not be valid if it accesses a private field which shadows a mutable field in the parent class" in {
    val tree = fromString("""
      // In A, x and y are -1.
      class A(var x: Int) {
        val y: Int = -1
      }

      // In B, x and y are 99 and private[this], implicitly so
      // in the case of x.
      class B(x: Int) extends A(-1) {
        private[this] def y: Int = 99

        // Three distinct results.
        def f = List(
          /* (99,99) */  (this.x, this.y), // warn
          /* (-1,99) */  ((this: B).x, (this: B).y),
          /* (-1,-1) */  ((this: A).x, (this: A).y)
        )

        // The 99s tell us we are reading the private[this]
        // data of a different instance.
        def g(b: B) = List(
          /* (-1,99) */  (b.x, b.y),
          /* (-1,99) */  ((b: B).x, (b: B).y),
          /* (-1,-1) */  ((b: A).x, (b: A).y)
        )
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not be valid if it accesses a constructor field which shadows a mutable field in the parent class" in {
    val tree = fromString("""
      class Test {
        class Base(var x: Int) { def increment() = { x = x + 1 } }
        class Derived(x: Int) extends Base(x) { override def toString = x.toString } // warn

        val derived = new Derived(1)
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be valid if it accesses a constructor field which shadows a non-mutable field in the parent class" in {
    val tree = fromString("""
      class Test {
        class Base(val x: Int)
        class Derived(x: Int) extends Base(x) { override def toString = x.toString } // no warn
      }
    """)

    global.ask { () => apply(rule)(tree) shouldBe empty }
  }
}
