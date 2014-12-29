package com.typesafe.abide.core

import scala.tools.abide.traversal._
import com.typesafe.abide.core._

class OptionImplicitTest extends TraversalTest {

  val rule = new OptionImplicit(context)

  "Option.apply" should "not be valid when its argument is an application of an implicit view" in {
    val tree = fromString("""
      class Test {
        class A
        class B
        implicit def aToB(a: A): B = new B()

        val nullA: A = null
        val optionB: Option[B] = Option(nullA)
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not be valid when given as an argument to a function if it has invalid arguments" in {
    val tree = fromString("""
      class Test {
        class A
        class B
        implicit def aToB(a: A): B = new B()

        val nullA: A = null
        def identityOptionB(x: Option[B]) = x
        identityOptionB(Option(nullA))
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be valid when no implicit view is applied to its argument" in {
    val tree = fromString("""
      class Test {
        class A
        class B

        val nullA: A = null
        def isNull(x: A) = x == null
        Option(nullA)
        Option(isNull(nullA))
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  "Methods other than Option.apply" should "be valid when their argument is an application of an implicit view" in {
    val tree = fromString("""
      class Test {
        class A
        class B
        implicit def aToB(a: A): B = new B()

        val nullA: A = null
        def identityB(b: B): B = b
        val optionB: Option[B] = Some(nullA)
        val otherB: B = identityB(nullA)
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }
}
