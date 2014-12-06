package scala.reflect.internal.traversal

import scala.reflect.internal.traversal._
import org.scalatest._

class MacroTest extends FlatSpec with Matchers with TreeProvider {

  trait TestingTraversal extends OptimizingTraversal {
    val universe : MacroTest.this.global.type = MacroTest.this.global

    type State = Null
    def emptyState = null

    def classes : Option[Set[Class[_]]] = step match {
      case c : ClassExtraction => c.classes
      case _ => None
    }

    def validate(classes : Class[_]*) : Boolean = this.classes == Some(classes.toSet)
  }

  "Extractor macro" should "work on basic quasiquotes" in {
    val traversal = new TestingTraversal {
      import universe._

      val step = optimize {
        case q"$mods val $name : $tpt = $_" =>
        case q"$mods var $name : $tpt = $_" =>
        case q"$mods def $name(..$vparamss) : $tpt = $_" =>
        case q"$scrut match { case ..$cases }" =>
        case q"$a = $b" =>
        case q"$a.selection" =>
        case q"$f(..$args)" =>
        case q"for (..$monads) $block" =>
        case q"for (..$monads) yield $block" =>
      }
    }

    val validation = traversal.validate(
      classOf[traversal.universe.ValDef],
      classOf[traversal.universe.DefDef],
      classOf[traversal.universe.Match],
      classOf[traversal.universe.Assign],
      classOf[traversal.universe.Select],
      classOf[traversal.universe.Apply],
      classOf[traversal.universe.ApplyToImplicitArgs],
      classOf[traversal.universe.ApplyImplicitView],
      classOf[traversal.universe.UnApply],
      traversal.universe.pendingSuperCall.getClass,
      traversal.universe.noSelfType.getClass
    )

    validation should be (true)
  }

  it should "work on basic tree matchers" in {
    val traversal = new TestingTraversal {
      import universe._

      val step = optimize {
        case id : Ident =>
        case vd : ValDef =>
        case DefDef(_, _, _, _, _, _) =>
        case m : Match =>
        case a : Assign =>
        case s : Select =>
        case ap : Apply =>
        case cd : ClassDef =>
        case md : ModuleDef =>
      }
    }

    val validation = traversal.validate(
      classOf[traversal.universe.ValDef],
      classOf[traversal.universe.DefDef],
      classOf[traversal.universe.Select],
      classOf[traversal.universe.Assign],
      classOf[traversal.universe.Match],
      classOf[traversal.universe.Ident],
      classOf[traversal.universe.Apply],
      classOf[traversal.universe.ApplyToImplicitArgs],
      classOf[traversal.universe.ApplyImplicitView],
      traversal.universe.pendingSuperCall.getClass,
      traversal.universe.noSelfType.getClass,
      classOf[traversal.universe.ClassDef],
      classOf[traversal.universe.ModuleDef]
    )

    validation should be (true)
  }

  it should "recover gracefully" in {
    val traversal = new TestingTraversal {
      import universe._

      val step = optimize {
        case t : Tree =>
      }
    }

    traversal.classes should be (None)
  }

  abstract class Extractor(val universe : MacroTest.this.global.type) extends OptimizingTraversal {
    import universe._

    type State = List[Tree]
    def emptyState = Nil

    def add(tree : Tree) : Unit = transform(tree :: _)
  }

  def verifyMatching(optimized : Extractor, normal : Extractor) : Unit = {
    val book = fromFile("traversal/AddressBook.scala")
    val interp = fromFile("traversal/SimpleInterpreter.scala")

    global.ask { () =>
      optimized.traverse(book)
      normal.traverse(book)
      assert(optimized.result == normal.result, "Matching failed on AddressBook.scala")
    }

    global.ask { () =>
      optimized.traverse(interp)
      normal.traverse(interp)
      assert(optimized.result == normal.result, "Matching failed on SimpleInterpreter.scala")
    }
  }

  "Quasiquote matching" should "work for var defs" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree @ q"$mods var $name : $tpt = $rhs" => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree @ q"$mods var $name : $tpt = $rhs" => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

  it should "work for val defs" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree @ q"$mods val $name : $tpt = $rhs" => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree @ q"$mods val $name : $tpt = $rhs" => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

  it should "work for def defs" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree @ q"$mods def $name(..$args) : $tpt = $rhs" => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree @ q"$mods def $name(..$args) : $tpt = $rhs" => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

  it should "work for assignments" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree @ q"$a = $b" => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree @ q"$a = $b" => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

  it should "work for selects" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree @ q"$a.$b" => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree @ q"$a.$b" => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

  it should "work for apply" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree @ q"$a(..$bs)" => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree @ q"$a(..$bs)" => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

  it should "work on for comprehensions" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree @ q"for (..$cs) $b" => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree @ q"for (..$cs) $b" => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

  it should "work on for-yield comprehensions" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree @ q"for (..$cs) yield $b" => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree @ q"for (..$cs) yield $b" => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

  it should "work for match statements" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree @ q"$a match { case ..$cs }" => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree @ q"$a match { case ..$cs }" => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

  "Subtype matching" should "work for binds" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree : Bind => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree : Bind => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

  it should "work for match statements" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree : Match => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree : Match => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

  it should "work for idents" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree : Ident => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree : Ident => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

  it should "work for selects" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree : Select => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree : Select => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

  it should "work for def defs" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree : DefDef => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree : DefDef => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

  it should "work for val defs" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree : ValDef => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree : ValDef => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

  it should "work for assignments" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree : Assign => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree : Assign => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

  it should "work for apply" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree : Apply => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree : Apply => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

  it should "work for class definitions" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree : ClassDef => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree : ClassDef => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

  it should "work for module definitions" in {
    val optimized = new Extractor(global) {
      import universe._
      val step = optimize { case tree : ModuleDef => add(tree) }
    }

    val normal = new Extractor(global) {
      import universe._
      val step : PartialFunction[Tree,Unit] = {
        case tree : ModuleDef => add(tree)
      }
    }

    verifyMatching(optimized, normal)
  }

}
