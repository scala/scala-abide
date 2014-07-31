package scala.tools.abide.test.directives

import scala.tools.abide.test._
import scala.tools.abide.directives._
import scala.tools.nsc._

class MutabilityTest extends AbideTest {

  object mutable extends {
    val universe: MutabilityTest.this.global.type = MutabilityTest.this.global
  } with MutabilityChecker

  import global._

  def mutableClass(tree: Tree, str: String): Boolean = global.ask { () =>
    val tpe = classByName(tree, str).toType
    mutable.publicMutable(tpe).isMutable
  }

  "Type immutability" should "be ensured in Lists" in {
    val tree = fromString("""
      sealed abstract class List[T]
      case class Cons[T](head: T, tail: List[T]) extends List[T]
      case object Nil extends List[Nothing]
    """)

    mutableClass(tree, "List") should be(false)
    mutableClass(tree, "Cons") should be(false)
    mutableClass(tree, "Nil") should be(false)
  }

  it should "be ensured in Trees" in {
    val tree = fromString("""
      sealed abstract class Tree[T]
      case class Node[T](t1: Tree[T], t2: Tree[T]) extends Tree[T]
      case class Leaf[T](value: T) extends Tree[T]
    """)

    mutableClass(tree, "Tree") should be(false)
    mutableClass(tree, "Node") should be(false)
    mutableClass(tree, "Leaf") should be(false)
  }

  it should "be ensured in types with immutable fields" in {
    val tree = fromString("""
      class ImmutableFields(a: Int, b: Boolean) {
        val map : Map[Int,Int] = Map(a -> 0)
      }
      class ImmutableFields2(list: List[Map[Int,List[_]]])
    """)

    mutableClass(tree, "ImmutableFields") should be(false)
    mutableClass(tree, "ImmutableFields2") should be(false)
  }

  it should "be ensured in types with cyclic references" in {
    val tree = fromString("""
      class A(b: B)
      class B {
        val a = new A(this)
      }
    """)

    mutableClass(tree, "A") should be(false)
    mutableClass(tree, "B") should be(false)
  }

  "Type mutability" should "be ensured in types with vars" in {
    val tree = fromString("""
      case class Test(head: Int, var tail: Int)
    """)

    mutableClass(tree, "Test") should be(true)
  }

  /* Well, maybe not...
  it should "be ensured in parents of mutable types" in {
    val tree = fromString("""
      sealed abstract class Parent[T]
      case class Child[T](head: T, private var tail: T) extends Parent[T]
    """)

    mutableClass(tree, "Parent") should be (true)
  }
  */

  /* Well, maybe not...
  it should "be ensured in parents of mutable objects" in {
    val tree = fromString("""
      sealed abstract class Parent[T]
      case class Child[T](head: T, private val tail: T) extends Parent[T]
      case object Mut extends Parent[Nothing] {
        var a = 0
      }
    """)

    mutableClass(tree, "Parent") should be (true)
  }
  */

  it should "be ensured in generically mutable types" in {
    val tree = fromString("""
      class Container {
        val p = (1, 2, scala.collection.mutable.Map.empty[Int,Int])
      }
    """)

    mutableClass(tree, "Container")
  }

}
