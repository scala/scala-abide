package scala.tools.abide.test.rules

import scala.tools.abide.test._
import com.typesafe.abide.sample._

class MatchCaseOnSeqTest extends AnalysisTest {
  import scala.tools.abide.traversal._

  val rule = new MatchCaseOnSeq(context)

  "Seqs" should "not be matched with ::" in {
    val tree = fromString("""
      class Toto {
        def test(list : Seq[Int]) = list match {
          case x :: xs => true
          case _ => false
        }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be (1) }
  }

  it should "be matched with Nil" in {
    val tree = fromString("""
      class Toto {
        def toto(list : Seq[Int]) = list match {
          case Nil => true
          case _ => false
        }
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be (true) }
  }

  it should "not be matched by :: (even when Nil is around)" in {
    val tree = fromString("""
      class Toto {
        def toto(list : Seq[Int]) = list match {
          case x :: xs => true
          case Nil => false
        }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be (1) }
  }

  it should "work fine on other matchers" in {
    val tree = fromString("""
      class Toto {
        def toto(list : Seq[Int]) = list match {
          case Seq(a, b) => true
          case _ => false
        }
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be (true) }
  }

  "Lists" should "accept :: and Nil as matchers" in {
    val tree = fromString("""
      class Toto {
        def toto(list : List[Int]) = list match {
          case x :: xs => true
          case Nil => false
        }
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be (true) }
  }

}
