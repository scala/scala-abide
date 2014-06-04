package scala.tools.abide
package traversal

class OrderingTest extends AbideTest with FusingTraversals {
  import global._

  object stackingTraverser extends {
    val analyzer : OrderingTest.this.type = OrderingTest.this
  } with Traversal {
    import analyzer.global._

    type State = List[Tree]
    def emptyState : State = Nil

    type Step = Tree => TraversalStep[Tree, State]

    def apply(tree : Tree, state : State) : Option[(State, Option[State => State])] = {
      val stepped = step(tree)
      Some(stepped.enter(state) -> stepped.leave)
    }

    val step : Step = (tree : Tree) => new TraversalStep[Tree, State] {
      val enter = (state : State) => tree :: state
      val leave : Option[State => State] = Some((state : State) => state match {
        case x :: xs if x == tree => xs
        case _ => state
      })
    }
  }

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

    global.ask { () => stackingTraverser.traverse(tree).isEmpty should be (true) }
  }

  it should "and in non-trivial code (AddressBook.scala)" in {
    val tree = fromFile("traversal/AddressBook.scala")
    global.ask { () => stackingTraverser.traverse(tree).isEmpty should be (true) }
  }

  it should "hold in non-trivial code (SimpleInterpreter.scala)" in {
    val tree = fromFile("traversal/SimpleInterpreter.scala")
    global.ask { () => stackingTraverser.traverse(tree).isEmpty should be (true) }
  }
}
