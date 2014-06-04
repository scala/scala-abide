package scala.tools.abide
package traversal

import org.scalatest.FunSuite

class SpeedAnalysis extends FunSuite with TreeProvider with FusingTraversals {
  import global._
  import org.scalatest._

  val processorCount = 100

  def speed(name : String, tree : Tree, traverser : Tree => List[Symbol]) : Long = {
    val start = System.currentTimeMillis
    val warnings = try {
      traverser(tree)
    } catch {
      case t : Throwable =>
        alert("misshap during traversal : " + t.getMessage)
        Nil
    }
    val size = warnings.size
    val time = System.currentTimeMillis - start
    info("name: " + name + " -> time="+time + ", warnings="+ size)
    time
  }

  class FastTraversalImpl(val analyzer : SpeedAnalysis.this.type) extends SimpleTraversal {
    import analyzer.global._

    type State = Map[Symbol, Boolean]
    def emptyState : State = Map.empty

    def ok(symbol : Symbol) : TraversalStep[Tree, State] = new SimpleStep[Tree, State] {
      val enter = (state : State) => state + (symbol -> true)
    }

    def nok(symbol : Symbol) : TraversalStep[Tree, State] = new SimpleStep[Tree, State] {
      val enter = (state : State) => state + (symbol -> state.getOrElse(symbol, false))
    }

    val step = optimize {
      case varDef @ q"$mods var $name : $tpt = $_" if varDef.symbol.owner.isMethod =>
        nok(varDef.symbol)
      case q"$rcv = $expr" =>
        ok(rcv.symbol)
    }
  }

  val fastTraverser = fuse((1 to processorCount).map { x => new FastTraversalImpl(this) } : _*)

  def traverseFast(tree : Tree) : List[Symbol] = fastTraverser.traverse(tree).toSeq.flatMap {
    x => x.asInstanceOf[Map[Symbol, Boolean]].collect { case (a, false) => a }
  }.toList

  def naiveTraverser(tree : Tree) : Map[Symbol, Boolean] = tree match {
    case varDef @ q"$mods var $name : $tpt = $_" if varDef.symbol.owner.isMethod =>
      varDef.children.map(naiveTraverser(_)).foldLeft(Map(varDef.symbol -> false)) { (all, next) =>
        (all.keys ++ next.keys).map(k => k -> (all.getOrElse(k, false) || next.getOrElse(k, false))).toMap
      }
    case assign @ q"$rcv = $expr" =>
      assign.children.map(naiveTraverser(_)).foldLeft(Map(rcv.symbol -> true)) { (all, next) =>
        (all.keys ++ next.keys).map(k => k -> (all.getOrElse(k, false) || next.getOrElse(k, false))).toMap
      }
    case tree =>
      tree.children.map(naiveTraverser(_)).foldLeft(Map.empty[Symbol,Boolean]) { (all, next) =>
        (all.keys ++ next.keys).map(k => k -> (all.getOrElse(k, false) || next.getOrElse(k, false))).toMap
      }
  }

  def traverseNaive(tree : Tree) : List[Symbol] = {
    (1 to processorCount).flatMap(x => naiveTraverser(tree).toSeq).collect { case (a,false) => a }.toList
  }

  case class ValidationState(map : Map[Symbol, Boolean]) {
    val issues : List[Symbol] = map.filter(!_._2).map(_._1).toList

    def merge(that : ValidationState) : ValidationState = {
      def merged(k : Symbol) = map.getOrElse(k, false) || that.map.getOrElse(k, false)
      ValidationState((map.keys ++ that.map.keys).map(k => k -> merged(k)).toMap)
    }
  }
  
  def naiveStatefulTraverser(tree : Tree) : ValidationState = tree match {
    case varDef @ q"$mods var $name : $tpt = $_" if varDef.symbol.owner.isMethod =>
      varDef.children.map(naiveStatefulTraverser(_)).foldLeft(ValidationState(Map(varDef.symbol -> false))) { (all, next) =>
        all merge next
      }
    case assign @ q"$rcv = $expr" =>
      assign.children.map(naiveStatefulTraverser(_)).foldLeft(ValidationState(Map(rcv.symbol -> true))) { (all, next) =>
        all merge next
      }
    case tree =>
      tree.children.map(naiveStatefulTraverser(_)).foldLeft(ValidationState(Map.empty[Symbol,Boolean])) { (all, next) =>
        all merge next
      }
  }

  def traverseStatefulNaive(tree : Tree) : List[Symbol] = {
    (1 to processorCount).flatMap(x => naiveStatefulTraverser(tree).issues).toList
  }

  /*
  // initialize traversals, they seem to be slow on first run sometimes...
  val tree = fromFile("traversal/AddressBook.scala")
  global.ask { () =>
    traverseFast(tree)
    traverseNaive(tree)
    traverseStatefulNaive(tree)
  }
  */

  ignore("Fast traversal is fast in AddressBook.scala") {
    val tree = fromFile("traversal/AddressBook.scala")
    global.ask { () =>
      val fastTime = speed("fast", tree, traverseFast)
      val naiveTime = speed("naive", tree, traverseNaive)
      val statefulNaiveTime = speed("naiveState", tree, traverseStatefulNaive)

      assert(fastTime < naiveTime, "Fusing should make simple rules at least faster")
      assert(fastTime < statefulNaiveTime, "Fusing should make simple rules at least faster, also against stateful approach")
    }
  }

  ignore("Fast traversal is fast in SimpleInterpreter.scala") {
    val tree = fromFile("traversal/SimpleInterpreter.scala")
    global.ask { () =>
      val fastTime = speed("fast", tree, traverseFast)
      val naiveTime = speed("naive", tree, traverseNaive)
      val statefulNaiveTime = speed("naiveState", tree, traverseStatefulNaive)

      assert(fastTime < naiveTime, "Fusing should make simple rules at least faster")
      assert(fastTime < statefulNaiveTime, "Fusing should make simple rules at least faster, also against stateful approach")
    }
  }

}
