package scala.reflect.internal.traversal.test

import scala.reflect.internal.traversal._
import org.scalatest._

class TraversalTest extends FlatSpec with Matchers with TreeProvider {

  object pathTraverser1 extends {
    val universe : TraversalTest.this.global.type = TraversalTest.this.global
  } with OptimizingTraversal {
    import universe._

    type State = Set[Tree]
    def emptyState : State = Set.empty

    def add(tree : Tree) { transform(_ + tree) }

    val step = optimize {
      case varDef @ q"$mods var $name : $tpt = $_" if varDef.symbol.owner.isMethod =>
        add(varDef)
      case tree @ q"$rcv = $expr" =>
        add(tree)
    }
  }

  def pathTraversal1(tree : global.Tree) : Set[global.Tree] = {
    import global._

    def rec(tree : Tree) : Set[Tree] = tree match {
      case tree : ValDef if tree.symbol.isVar && tree.symbol.owner.isMethod =>
        tree.children.flatMap(rec(_)).toSet + tree
      case tree : Assign =>
        tree.children.flatMap(rec(_)).toSet + tree
      case _ => tree.children.flatMap(rec(_)).toSet
    }

    rec(tree)
  }

  "Traversal completeness on vars and assigns" should "be valid in AddressBook.scala" in {
    val tree = fromFile("traversal/AddressBook.scala")
    global.ask { () =>
      pathTraverser1.traverse(tree)
      pathTraverser1.result should be (pathTraversal1(tree))
    }
  }

  it should "be valid in SimpleInterpreter.scala" in {
    val tree = fromFile("traversal/SimpleInterpreter.scala")
    global.ask { () =>
      pathTraverser1.traverse(tree)
      pathTraverser1.result should be (pathTraversal1(tree))
    }
  }

  object pathTraverser2 extends {
    val universe : TraversalTest.this.global.type = TraversalTest.this.global
  } with ScopingTraversal with OptimizingTraversal {
    import universe._

    type State = (List[Tree], Set[(Option[Tree], Tree)])
    def emptyState : State = (Nil, Set.empty)

    def add(tree : Tree) {
      transform(state => (state._1, state._2 + (state._1.headOption -> tree)))
    }

    def enter(tree : Tree) {
      transform(state => (tree :: state._1, state._2), state => state._1 match {
        case x :: xs if x == tree => (xs, state._2)
        case _ => state
      })
    }

    val step = optimize {
      case dd : DefDef =>
        for (vparams <- dd.vparamss; vparam <- vparams) add(vparam)
        enter(dd)
      case varDef @ q"$mods var $name : $tpt = $_" if varDef.symbol.owner.isMethod =>
        add(varDef)
    }
  }

  def pathTraversal2(tree : global.Tree) : Set[(Option[global.Tree], global.Tree)] = {
    import global._

    def rec(tree : Tree, parent : Option[Tree]) : Set[(Option[Tree], Tree)] = tree match {
      case dd : DefDef =>
        dd.vparamss.flatten.map(p => parent -> p).toSet ++ dd.children.flatMap(rec(_, Some(dd)))
      case tree : ValDef if tree.symbol.isVar && tree.symbol.owner.isMethod =>
        Set(parent -> tree) ++ tree.children.flatMap(rec(_, parent))
      case _ => tree.children.flatMap(rec(_, parent)).toSet
    }

    rec(tree, None)
  }

  def niceTest(tree : global.Tree) {
    pathTraverser2.traverse(tree)
    val fast = pathTraverser2.result._2
    val naive = pathTraversal2(tree)

    def simplify(set: Set[(Option[global.Tree], global.Tree)]) : Set[String] = {
      set.map(p => p._1.map(t => t.symbol.toString) + " -> " + p._2.symbol.toString)
    }

    val simpleFast = simplify(fast)
    val simpleNaive = simplify(naive)
    val intersection = simpleFast intersect simpleNaive
    val fastError = (simpleFast -- intersection).toList.sorted
    val naiveError = (simpleNaive -- intersection).toList.sorted

    assert(fast == naive, fastError.toString + "\n not equal to \n" + naiveError.toString)
  }

  "Traversal completeness on scoping" should "be valid in AddressBook.scala" in {
    val tree = fromFile("traversal/AddressBook.scala")
    global.ask { () => niceTest(tree) }
  }

  it should "be valid in SimpleInterpreter.scala" in {
    val tree = fromFile("traversal/SimpleInterpreter.scala")
    global.ask { () => niceTest(tree) }
  }
}
