package scala.tools.abide
package tests.rules

class MatchCaseOnSeqTest extends tests.AbideTest {

  object analyzer extends {
    val global : MatchCaseOnSeqTest.this.global.type = MatchCaseOnSeqTest.this.global
  } with Analyzer

  object rule extends scala.tools.abide.rules.MatchCaseOnSeq(analyzer)
  analyzer.register(rule)

  "Seqs" should "not be matched with ::" in {
    val tree = fromString("""
      class Toto {
        def test(list : Seq[Int]) = list match {
          case x :: xs => true
          case _ => false
        }
      }
    """)

    global.ask { () => analyzer(tree).size should be (1) }
  }

  it should "not be matched with Nil" in {
    val tree = fromString("""
      class Toto {
        def toto(list : Seq[Int]) = list match {
          case Nil => true
          case _ => false
        }
      }
    """)

    global.ask { () => analyzer(tree).size should be (1) }
  }

  it should "not be matched by either" in {
    val tree = fromString("""
      class Toto {
        def toto(list : Seq[Int]) = list match {
          case x :: xs => true
          case Nil => false
        }
      }
    """)

    global.ask { () => analyzer(tree).size should be (2) }
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

    global.ask { () => analyzer(tree).isEmpty should be (true) }
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

    global.ask { () => analyzer(tree).isEmpty should be (true) }
  }

}
