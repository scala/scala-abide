package com.typesafe.abide.core.test

import scala.tools.abide.test.traversal._
import com.typesafe.abide.core._

class UnusedMemberTest extends TraversalTest {

  val rule = new UnusedMember(context)

  "Unused local members" should "be discovered when simple" in {
    val tree = fromString("""
      class Toto {
        private val titi = 2
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be discovered when private[this]" in {
    val tree = fromString("""
      class Toto {
        private[this] val titi = 2
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be discovered when `def`" in {
    val tree = fromString("""
      class Toto {
        private def titi = 0
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  // can't actually be found in the compiler plugin because these values will be
  // folded by the type-checker (and therefore won't be used elsewhere), so we ignore them
  ignore should "be discovered when private final" in {
    val tree = fromString("""
      class Toto {
        private final val titi = 0
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be ignored when synthetic" in {
    val tree = fromString("""
      class Toto {
        def test(list : List[Int]) : List[Int] = list.map(_ => 1)
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be ignored when used in super-constructor" in {
    val tree = fromString("""
      class Foo(val a : Int)
      class Bar(b : Int) extends Foo(b)
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be reported when NOT used in super-constructor" in {
    val tree = fromString("""
      class Foo(val a : Int)
      class Bar(b : Int) extends Foo(1)
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "be ignored when used in case statements" in {
    val tree = fromString("""
      class Toto {
        def test(l : List[Int]) : Int = l match {
          case x :: _ => x
          case _ => 0
        }
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be reported when NOT used in rhs of case statement" in {
    val tree = fromString("""
      class Toto {
        def test(l : List[Int]) : Int = l match {
          case x :: xs => x
          case a => 0
        }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(2) }
  }

  it should "be found in Properties.scala" in {
    val tree = fromFile("Properties.scala")
    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  "Other members" should "be valid when used" in {
    val tree = fromString("""
      class Toto {
        private def titi = 0
        println(titi)
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be valid when private final is used" in {
    val tree = fromString("""
      class Toto {
        final private val test = 0
        def toto = test + 1
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be valid when private final is used in objects" in {
    val tree = fromString("""
      object Titi {
        final private val test = 0
      }
      class Titi {
        import Titi._
        def toto = test + 1
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be valid when public" in {
    val tree = fromString("""
      class Toto {
        val tree = 0
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be valid when package-private" in {
    val tree = fromString("""
      package toto
      class Toto {
        private[toto] val tree = 0
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be valid in MurmurHash.scala" in {
    val tree = fromFile("MurmurHash.scala")
    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be valid in MurmurHash.scala alongside other rules" in {
    val tree = fromFile("MurmurHash.scala")
    val rules = List(
      rule,
      new MatchCaseOnSeq(context),
      new PublicMutable(context),
      new RenamedDefaultParameter(context),
      new StupidRecursion(context),
      new LocalValInsteadOfVar(context),
      new MemberValInsteadOfVar(context)
    )
    global.ask { () => apply(rules: _*)(tree).isEmpty should be(true) }
  }
}
