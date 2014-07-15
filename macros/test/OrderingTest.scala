package scala.reflect.internal.traversal.test

import scala.reflect.internal.traversal._
import org.scalatest._

class OrderingTest extends FlatSpec with Matchers with TreeProvider {
  import global._

  object stackingTraverser extends {
    val universe : OrderingTest.this.global.type = OrderingTest.this.global
  } with ScopingTraversal {
    import universe._

    type State = List[Tree]
    def emptyState : State = Nil

    val step : PartialFunction[Tree, Unit] = {
      case tree => transform(tree :: _, state => state match {
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

    global.ask { () =>
      stackingTraverser.traverse(tree)
      stackingTraverser.result.isEmpty should be (true)
    }
  }

  it should "and in non-trivial code (AddressBook.scala)" in {
    val tree = fromFile("traversal/AddressBook.scala")
    global.ask { () =>
      stackingTraverser.traverse(tree)
      stackingTraverser.result.isEmpty should be (true)
    }
  }

  it should "hold in non-trivial code (SimpleInterpreter.scala)" in {
    val tree = fromFile("traversal/SimpleInterpreter.scala")
    global.ask { () =>
      stackingTraverser.traverse(tree)
      stackingTraverser.result.isEmpty should be (true)
    }
  }
}
