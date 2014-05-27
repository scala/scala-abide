package scala.tools.abide
package tests

class TraversalTests extends AbideTest {
  
  class StackingWarning(tree : Tree) extends Warning
  class StackingState(stack : List[Tree]) extends State {
    def warnings : List[Warning] = stack.map(new StackingWarning(_))
  }

  class StackingRule extends TraversalRule {
    type Step = Tree => TraversalStep
    def apply(tree : Tree, state : RuleState) = {
      val stepped = step(tree)
      step.enter(state) -> step.leave
    }

    type RuleState = StackingState
    def emptyState = new StackingState(Nil)

    val step : Step = (tree : Tree) => new TraversalStep[Tree, StackingState] {
      def enter(state : StackingState) : StackingState = new StackingState(tree :: state.stack)
      def leave : Option[StackingState => StackingState] = Some((state : StackingState) => state.stack match {
        case x :: xs if x == tree => new StackingState(xs)
        case _ => state
      })
    }
  }

  object analyzer extends {
    val global : TraversalTests.this.global.type = TraversalTests.this.global
  } with Analyzer

  analyzer.register(new StackingRule(analyzer))

  "Traversal ordering" should "be valid in trivial code" in {
    val tree = fromString("""
      package toto
      class Toto {
        private var a = 10
        def toto(i : Int) : Int = {
          a += 1
          a + i
        }
      }
    """)

    global.ask { () => analyzer(tree).isEmpty should be (true) }
  }

  it should "and in non-trivial code (AddressBook.scala)" in {
    val tree = fromFile("traversal/AddressBook.scala")
    global.ask { () => analyzer(tree).isEmpty should be (true) }
  }

  it should "hold in non-trivial code (SimpleInterpreter.scala)" in {
    val tree = fromFile("traversal/SimpleInterpreter.scala")
    global.ask { () => analyzer(tree).isEmpty should be (true) }
  }
}
