package com.lightbend.abide.core

import scala.tools.abide.traversal._
import com.lightbend.abide.core._

class InferAnyTest extends TraversalTest {

  val rule = new InferAny(context)

  "Inferences of Any" should "not be valid if not annotated" in {
    val tree = fromString("""
      class Test {
        List(1, 2, 3) contains "a"
        1L to 10L contains 3
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(2) }
  }

  it should "not be valid in methods if not annotated" in {
    val tree = fromString("""
      class Test {
        def get(x: => Option[Int]) = x getOrElse Some(5)
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be valid if applied method is explicitly applied to Any" in {
    val tree = fromString("""
      class Test {
        List(1, 2, 3) contains[Any] "a"
        1L to 10L contains[Any] 3
      }
    """)

    global.ask { () => apply(rule)(tree) shouldBe empty }
  }

  it should "be valid if the argument is explicitly ascribed Any" in {
    val tree = fromString("""
      class Test {
        def get(x: => Option[Int]) = x getOrElse (Some(5): Any)
      }
    """)

    global.ask { () => apply(rule)(tree) shouldBe empty }
  }

  it should "not be valid only once for case classes" in {
    val tree = fromString("""
      case class Test(x: Boolean = 1L to 10L contains 3)
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not be valid if Any is inferred as part of a tuple" in {
    val tree = fromString("""
      class Test {
        ((1L to 10L) zip (11L to 20L)).contains((3, 15L))
        ((1L to 10L) zip (11L to 20L)).contains(((3: Any), 15L))
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }
}
