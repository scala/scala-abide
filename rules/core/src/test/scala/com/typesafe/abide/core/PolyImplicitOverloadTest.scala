package com.typesafe.abide.core

import scala.tools.abide.traversal._
import com.typesafe.abide.core._

class PolyImplicitOverloadTest extends TraversalTest {

  val rule = new PolyImplicitOverload(context)

  "Implicit methods" should "be valid when monomorphic and not overloaded" in {
    val tree = fromString("""
      object Test {
        implicit def meth(x: List[Int]): Map[Int, Int] = Map()
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be valid when polymorphic and not overloaded" in {
    val tree = fromString("""
      object Test {
        implicit def meth[T](x: List[T]): Map[T, T] = Map()
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be valid when monomorphic and overloaded" in {
    val tree = fromString("""
      object Test {
        implicit def meth(x: List[Int]): Map[Int, Int] = Map()
        implicit def meth(x: Set[Int]): Map[Int, Int] = Map()
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "not be valid when polymorphic and overloaded" in {
    val tree = fromString("""
      object Test {
        implicit def meth[T](x: List[T]): Map[T, T] = Map()
        implicit def meth[T](x: Set[T]): Map[T, T] = Map()
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(2) }
  }

  "Implicit classes" should "be valid" in {
    val tree = fromString("""
      package object foo {
        implicit class Bar[T](val x: T) extends AnyVal {
          def bippy = 1
        }
      }

      package foo {
        object Baz {
          def main(args: Array[String]): Unit = {
            "abc".bippy
          }
        }
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }
}
