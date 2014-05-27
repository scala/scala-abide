package scala.tools.abide
package tests

import traversal._

class TraversalTests extends AbideTest {
  
  class CounterRule(val traverser : Traversal) extends TraversalRule {
    import traverser.global._

    class VisitingWarning(tree : Tree) extends Warning

    class VisitingState(trees : List[Tree], errors : List[Tree]) extends State {
      def enter(tree : Tree) = new VisitingState(tree :: trees, errors)
      def leave(tree : Tree) = {
        if (trees.nonEmpty && trees.head == tree) new VisitingState(trees.tail, errors)
        else new VisitingState(trees, tree :: errors)
      }

      def warnings = errors.map(new VisitingWarning(_))
    }

    type RuleState = VisitingState

    val name = "counter-rule"

    def emptyState = new VisitingState(Nil, Nil)

    val step = next {
      case tree => new TraversalStep[Tree, VisitingState] {
        val enter = (state : VisitingState) => state.enter(tree)
        val leave = Some((state : VisitingState) => state.leave(tree))
      }
    }
  }

  object analyzer extends {
    val global : TraversalTests.this.global.type = TraversalTests.this.global
  } with Analyzer

  analyzer.register(new CounterRule(analyzer))

  "Traversal" should "guarantee order in simple code" in {
    val tree = fromString("""
      class Toto {
        def toto(i : Int) = i + 2
      }
    """)

    global.ask { () => analyzer(tree).isEmpty should be (true) }
  }

  it should "guarantee order in real code too" in {
    def analyzeTraversal(fileName : String) = {
      val file = new java.io.File(getClass.getResource("/traversal/" + fileName).toURI)
      val tree = fromFile(file.getAbsolutePath)
      global.ask { () => analyzer(tree).isEmpty should be (true) }
    }

    analyzeTraversal("AddressBook.scala")
    analyzeTraversal("Interpreter.scala")
  }
}
