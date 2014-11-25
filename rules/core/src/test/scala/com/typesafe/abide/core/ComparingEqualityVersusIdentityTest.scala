package com.typesafe.abide.core

import scala.tools.abide.traversal._
import com.typesafe.abide.core._

class ComparingEqualityVersusIdentityTest extends TraversalTest {

  val rule = new ComparingEqualityVersusIdentity(context)

  "Calling equals" should "not be warned on classes that provide a concrete implementation" in {
    val tree = fromString("""
              class Toto {
                def equals(that: Any): Boolean = that.isInstanceOf[Toto]
              }
              object Foo {
                val toto1 = new Toto
                val toto2 = new Toto
                toto1 eq toto2      // false
                toto1 equals toto2  // true and no warning because Toto provides an implementation of equals
                toto1 == toto2      // calling `==` or `equals` is the same
              }
            """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  it should "not be warned if concrete implementation of equals is inherited" in {
    val tree = fromString("""
                class A extends Equals {
                  def equals(that: Any): Boolean = canEqual(that)
                  def canEqual(that: Any): Boolean = that.isInstanceOf[A]
                }
                class Toto extends A {
                  override def canEqual(that: Any): Boolean = that.isInstanceOf[Toto]
                }
                object Foo {
                  val toto1 = new Toto
                  val toto2 = new Toto
                  toto1 eq toto2      // false
                  toto1 equals toto2  // true and no warning because Toto provides (via inheritance) an implementation of equals
                }
              """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  it should "not be warned on case classes" in {
    val tree = fromString("""
                    object Foo {
                      case class Toto(v: Int)
                      val toto1 = new Toto(1)
                      val toto2 = new Toto(1)
                      toto1 eq toto2      // false
                      toto1 equals toto2  // true and no warning because Toto provides an implementation of equals
                    }
                  """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  it should "not be warned on case objects" in {
    val tree = fromString("""
                  object Foo {
                    case object Toto
                    val toto1 = Toto
                    val toto2 = Toto
                    toto1 eq toto2      // true
                    toto1 equals toto2  // true and no warning because Toto is a singleton
                  }
                """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  it should "be warned when no concrete implementation of equals is provided" in {
    val tree = fromString("""
                  object Foo {
                    class Toto
                    val toto1 = new Toto
                    val toto2 = new Toto
                    toto1 eq toto2      // false
                    toto1 equals toto2  // false and a warning because Toto does NOT provide an implementation of equals
                  }
                """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not be warned on objects" in {
    val tree = fromString("""
                object Foo {
                  object Toto
                  val toto1 = Toto
                  val toto2 = Toto
                  toto1 eq toto2      // false
                  toto1 equals toto2  // false and no warning because Toto is a singleton
                }
              """)

    global.ask { () => apply(rule)(tree).size should be(0) }
  }

  it should "be warned when a class does not implement equals with the expected signature" in {
    val tree = fromString("""
                class Toto {
                  // all implementation of equals here have the wrong signature
                  def equals(that: AnyRef): Boolean = false
                  def equals(that: Toto): Boolean = false
                  def equals(that: Any): Int = 2
                  def equals(that: Any)(implicit ev: Any): Boolean = true
                }
                object Foo {
                  val toto1 = new Toto
                  val toto2 = new Toto
                  toto1 eq toto2      // false
                  toto1 equals toto2  // false and a warning because Toto does NOT provide an implementation of equals
                }
              """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }
}