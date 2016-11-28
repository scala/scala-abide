package com.typesafe.abide.core

import scala.tools.abide.traversal._
import com.typesafe.abide.core._

class TypeParameterShadowTest extends TraversalTest {

  val rule = new TypeParameterShadow(context)

  "Method type parameter" should "not be valid if it shadows another type" in {
    val tree = fromString("""
      trait D
      trait Test {
        def foobar[D](in: D) = in.toString // warn
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not be valid if it shadows another type when nested" in {
    val tree = fromString("""
      trait Test {
        def foobar[N[M[List[_]]]] = 1 // warn
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be valid if it has the same name as another parameter in the same list" in {
    val tree = fromString("""
      trait Test {
        def foobar[A, N[M[L[A]]]] = 1 // no warn
      }
    """)

    global.ask { () => apply(rule)(tree) shouldBe empty }
  }

  "Type member type parameter" should "not be valid if it shadows another type" in {
    val tree = fromString("""
      trait D
      trait Test {
        type MySeq[D] = Seq[D] // warn
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not be valid if it shadows another type when nested" in {
    val tree = fromString("""
      trait Test {
        type E[M[List[_]]] = Int // warn
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be valid if it has the same name as another parameter in the same list" in {
    val tree = fromString("""
      trait Test {
        type G[A, M[L[A]]] = Int // no warn
      }
    """)

    global.ask { () => apply(rule)(tree) shouldBe empty }
  }

  "Class type parameter" should "not be valid if it shadows another type" in {
    val tree = fromString("""
      trait Test {
        trait T
        class Foo[T](t: T) { // warn
          def bar[T](w: T) = w.toString // warn
        }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(2) }
  }

  it should "not be valid if it shadows another type when nested" in {
    val tree = fromString("""
      class C[M[List[_]]] // warn
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be valid if it has the same name as another parameter in the same list" in {
    val tree = fromString("""
      class F[A, M[L[A]]] // no warn
    """)

    global.ask { () => apply(rule)(tree) shouldBe empty }
  }

  it should "not be valid if the parameter's name is List" in {
    val tree = fromString("""
      class G[List] // warn
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not be valid if the parameter's name is Byte" in {
    val tree = fromString("""
      class F[Byte] // warn
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "not be valid if it shadows a type alias" in {
    val tree = fromString("""
      object TypeAliasTest {
        type T = Int
        class F[T] // warn
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }
}
