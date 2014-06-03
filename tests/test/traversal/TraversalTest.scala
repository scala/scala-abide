package scala.tools.abide
package traversal

class TraversalTests extends AbideTest {
  import global._
  
  class StackingRule(val analyzer : Analyzer) extends TraversalRule {
    import analyzer.global._

    val name = "stacking-rule"

    class StackingWarning(tree : Tree) extends Warning
    class StackingState(val stack : List[Tree]) extends State {
      def warnings : List[Warning] = stack.map(new StackingWarning(_))
    }

    type Step = Tree => TraversalStep[Tree, StackingState]

    def apply(tree : Tree, state : StackingState) :
             Option[(StackingState, Option[StackingState => StackingState])] = {
      val stepped = step(tree)
      Some(stepped.enter(state) -> stepped.leave)
    }

    type RuleState = StackingState
    def emptyState = new StackingState(Nil)

    val step : Step = (tree : Tree) => new TraversalStep[Tree, StackingState] {
      val enter = (state : StackingState) => new StackingState(tree :: state.stack)
      val leave : Option[StackingState => StackingState] = Some((state : StackingState) => state.stack match {
        case x :: xs if x == tree => new StackingState(xs)
        case _ => state
      })
    }
  }

  object analyzer extends {
    val global : TraversalTests.this.global.type = TraversalTests.this.global
  } with Analyzer {
    val components = Seq(new FastTraversal(this))
    val rules = Seq(new StackingRule(this))
  }

  analyzer.enableAll

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
