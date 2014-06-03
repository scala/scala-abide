package scala.tools.abide
package traversal

import rules._
import org.scalatest.FunSuite

class SpeedAnalysis extends FunSuite with CompilerProvider with TreeProvider {
  import global._
  import org.scalatest._

  val processorCount = 100

  class ValInsteadOfVar(val analyzer : Analyzer) extends ExistentialRule {
    import analyzer.global._

    type Elem = Symbol

    val name = "test-rule"

    val step = optimize {
      case varDef @ q"$mods var $name : $tpt = $_" if varDef.symbol.owner.isMethod =>
        nok(varDef.symbol)
      case q"$rcv = $expr" =>
        ok(rcv.symbol)
    }
  }

  ignore("Fast traversal speed") {
    object analyzer extends {
      val global : SpeedAnalysis.this.global.type = SpeedAnalysis.this.global
    } with Analyzer {
      val components = Seq(new FastTraversal(this))
      val rules = (1 to processorCount).toSeq.map(i => new ValInsteadOfVar(this))
    }

    analyzer.enableAll

    global.ask { () =>
      val ts1 = System.currentTimeMillis
      val trees : List[Tree] = fromFolder("/Users/nvoirol/scala/src/library").force.toList
      var errors = 0
      val ts = System.currentTimeMillis
      val warnings = trees.flatMap { tree =>
        try {
          analyzer(tree)
        } catch {
          case _ : Throwable =>
            errors += 1
            Nil
        }
      }
      val time = System.currentTimeMillis - ts
      info("Fast traversal : compile time="+(ts - ts1) + ", analysis time="+time + ", warnings="+ warnings.size)
    }
  }

  class NaiveValVar {
    def traverse(tree : Tree) : Map[Symbol, Boolean] = tree match {
      case varDef @ q"$mods var $name : $tpt = $_" if varDef.symbol.owner.isMethod =>
        varDef.children.map(traverse(_)).foldLeft(Map(varDef.symbol -> false)) { (all, next) =>
          (all.keys ++ next.keys).map(k => k -> (all.getOrElse(k, false) || next.getOrElse(k, false))).toMap
        }
      case assign @ q"$rcv = $expr" =>
        assign.children.map(traverse(_)).foldLeft(Map(rcv.symbol -> true)) { (all, next) =>
          (all.keys ++ next.keys).map(k => k -> (all.getOrElse(k, false) || next.getOrElse(k, false))).toMap
        }
      case tree =>
        tree.children.map(traverse(_)).foldLeft(Map.empty[Symbol,Boolean]) { (all, next) =>
          (all.keys ++ next.keys).map(k => k -> (all.getOrElse(k, false) || next.getOrElse(k, false))).toMap
        }
    }

    def apply(tree : Tree) : List[Symbol] = {
      val mapping = traverse(tree)
      mapping.toList.collect { case (sym, false) => sym }
    }
  }

  ignore("Naive traversal speed") {
    val rules = for (i <- 1 to processorCount) yield new NaiveValVar

    global.ask { () =>
      val ts1 = System.currentTimeMillis
      val trees : List[Tree] = fromFolder("/Users/nvoirol/scala/src/library").force.toList
      var errors = 0
      val ts = System.currentTimeMillis
      val warnings = trees.flatMap { tree =>
        try {
          rules.flatMap(rule => rule(tree))
        } catch {
          case _ : Throwable =>
            errors += 1
            Nil
        }
      }
      val time = System.currentTimeMillis - ts
      info("Naive traversal : compile time="+(ts - ts1) + ", analysis time="+time + ", warnings="+ warnings.size)
    }
  }

  case class ValidationState(map : Map[Symbol, Boolean]) {
    val issues : List[Symbol] = map.filter(!_._2).map(_._1).toList

    def merge(that : ValidationState) : ValidationState = {
      def merged(k : Symbol) = map.getOrElse(k, false) || that.map.getOrElse(k, false)
      ValidationState((map.keys ++ that.map.keys).map(k => k -> merged(k)).toMap)
    }
  }
  
  class StatefulNaiveValVar {
    def traverse(tree : Tree) : ValidationState = tree match {
      case varDef @ q"$mods var $name : $tpt = $_" if varDef.symbol.owner.isMethod =>
        varDef.children.map(traverse(_)).foldLeft(ValidationState(Map(varDef.symbol -> false))) { (all, next) =>
          all merge next
        }
      case assign @ q"$rcv = $expr" =>
        assign.children.map(traverse(_)).foldLeft(ValidationState(Map(rcv.symbol -> true))) { (all, next) =>
          all merge next
        }
      case tree =>
        tree.children.map(traverse(_)).foldLeft(ValidationState(Map.empty[Symbol,Boolean])) { (all, next) =>
          all merge next
        }
    }

    def apply(tree : Tree) : List[Symbol] = traverse(tree).issues
  }

  ignore("Stateful naive traversal speed") {
    val rules = for (i <- 1 to processorCount) yield new StatefulNaiveValVar

    global.ask { () =>
      val ts1 = System.currentTimeMillis
      val trees : List[Tree] = fromFolder("/Users/nvoirol/scala/src/library").force.toList
      var errors = 0
      val ts = System.currentTimeMillis
      val warnings = trees.flatMap { tree =>
        try {
          rules.flatMap(rule => rule(tree))
        } catch {
          case _ : Throwable =>
            errors += 1
            Nil
        }
      }
      val time = System.currentTimeMillis - ts
      info("Naive traversal : compile time="+(ts - ts1) + ", analysis time="+time + ", warnings="+ warnings.size)
    }
  }


}
