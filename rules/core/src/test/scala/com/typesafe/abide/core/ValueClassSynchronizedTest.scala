package com.typesafe.abide.core

import scala.tools.abide.traversal._
import com.typesafe.abide.core._

class ValueClassSynchronizedTest extends TraversalTest {

  val rule = new ValueClassSynchronized(context)

  it should "not be valid when accidentally calling Predef.synchronized" in {
    val tree = fromString("""
      class C(val self: AnyRef) extends AnyVal {
        def foo = synchronized { toString }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be valid calling some other synchronized" in {
    val tree = fromString("""
      class C(val self: AnyRef) {
        def foo = self.synchronized { toString }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  it should "be valid outside of value class" in {
    val tree = fromString("""
      class C {
        def foo = Predef.synchronized { toString }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  it should "be valid in class nested in value class" in {
    val tree = fromString("""
      class C(val self: AnyRef) {
        def foo = { new { def foo = synchronized { toString } } }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

}
