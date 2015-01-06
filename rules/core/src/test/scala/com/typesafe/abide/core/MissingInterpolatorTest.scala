package com.typesafe.abide.core

import scala.tools.abide.traversal._
import com.typesafe.abide.core._

class MissingInterpolatorTest extends TraversalTest {

  val rule = new MissingInterpolator(context)

  "String literal" should "not be valid if it contains quoted names of values in scope" in {
    val tree = fromString("""
      class A {
        val bippy = 123

        def f = "Put the $bippy in the $bippy" // warn
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be valid if it contains quoted names of values not in scope" in {
    val tree = fromString("""
      class B {
        val dingus = 123

        def f = "Put the $bippy in the $bippy" // no warn
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  it should "not be valid if it contains quoted code" in {
    val delim = "\"\"\""
    val tree = fromString("""
      class C {
        def f = """ + delim + """Put the ${println("bippy")} in the bippy!""" + delim + """
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not be valid if it contains quoted names of methods defined in the same package" in {
    val tree = fromString("""
      package object test {
        def aleppo = 9
      }
      package test {
        class E {
          def f = "$aleppo is a pepper and a city." // warn
          def k = s"Just an interpolation of $aleppo" // no warn
        }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not be valid if it contains quoted names of private methods defined in parent class" in {
    val tree = fromString("""
      class Bar {
        private def bar = 8
        if (bar > 8) ???       // use it to avoid extra warning
      }
      class Baz extends Bar {
        def f = "$bar is private, shall we warn just in case?" // warn
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not be valid if it contains quoted names of methods which take no explicit arguments" in {
    val tree = fromString("""
      class G {
        def greppo(n: Int) = ???
        def zappos(n: Int)(implicit ord: math.Ordering[Int]) = ???
        def hippo(implicit n: Int) = ???
        def g = "$greppo takes an arg" // no warn
        def z = "$zappos takes an arg too" // no warn
        def h = "$hippo takes an implicit" // warn
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be valid if it contains quoted names of methods which take explicit arguments" in {
    val tree = fromString("""
      class J {
        def j = 8
        class J2 {
          def j(i: Int) = 2 * i
          def jj = "shadowed $j"  // no warn
        }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  it should "be valid in implicitNotFound annotations if it contains quoted names of objects not in scope" in {
    val tree = fromString("""
      package test {
        @scala.annotation.implicitNotFound(msg = "Cannot construct a collection of type ${To} with elements of type ${Elem} based on a collection of type ${From}.") // no warn
        trait CannotBuildFrom1[-From, -Elem, +To]

        @scala.annotation.implicitNotFound("Cannot construct a collection of type ${To} with elements of type ${Elem} based on a collection of type ${From}.") // no warn
        trait CannotBuildFrom2[-From, -Elem, +To]

        import annotation._
        @implicitNotFound(msg = "Cannot construct a collection of type ${To} with elements of type ${Elem} based on a collection of type ${From}.") // no warn
        trait CannotBuildFrom3[-From, -Elem, +To]

        @implicitNotFound("No Z in ${A}")   // no warn
        class Z[A]
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  it should "not be valid if it contains quoted names of methods with one overloaded alternatives which take no explicit arguments" in {
    val tree = fromString("""
      class Doo {
        def beppo(i: Int) = 8 * i
        def beppo = 8
        class Dah extends Doo {
          def f = "$beppo was a marx bros who saw dollars."  // warn
        }
        def g = "$beppo is overloaded" // warn
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(2) }
  }

  it should "be valid if it contains quoted names of methods which take curried explicit arguments" in {
    val tree = fromString("""
      class Curry1 {
        def bunko()(x: Int): Int = 5
        def f1 = "I was picked up by the $bunko squad" // no warn
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  it should "not be valid if it contains quoted names of unitary methods" in {
    val tree = fromString("""
      class Curry2 {
        def groucho(): Int = 5
        def f2 = "I salute $groucho" // warn
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not be valid if it contains quoted names of methods with many empty argument lists" in {
    val tree = fromString("""
      class Curry3 {
        def dingo()()()()()(): Int = 5 // kind of nuts this can be evaluated with just 'dingo', but okay
        def f3 = "I even salute $dingo" // warn
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not be valid if it contains quoted names of methods with many empty argument lists and type arguments" in {
    val tree = fromString("""
      class Curry4 {
        def calico[T1, T2]()()(): Int = 5 // even nutsier
        def f4 = "I also salute $calico" // warn 9
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be valid if it contains quoted names of methods with at least one non-empty argument list" in {
    val tree = fromString("""
      class Curry5 {
        def palomino[T1, T2]()(y: Int = 5)(): Int = 5 // even nutsier
        def f5 = "I draw the line at $palomino" // no warn
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  it should "not be valid if it contains quoted names of values defined in the same method" in {
    val tree = fromString("""
      class Test {
        def method() = {
          val x = 3
          def y = 5
          val s = "1 + 2 + $x"
          "1 + 2 + $y"
        }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(2) }
  }

  it should "not be valid if it contains a quoted name of a method parameter" in {
    val tree = fromString("""
      class Test {
        def method(arg: Int) = "method's argument is $arg"
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }
}
