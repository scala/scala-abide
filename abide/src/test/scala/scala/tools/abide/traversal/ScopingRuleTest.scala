package scala.tools.abide.traversal

import scala.tools.abide._
import scala.tools.abide.traversal._
import scala.tools.abide.directives._

class ScopingRuleTest extends TraversalTest {

  object rule extends {
    val context: ScopingRuleTest.this.context.type = ScopingRuleTest.this.context
  } with ScopingRule {
    import context._
    import context.universe._

    val name = "scoping-rule"

    case class Warning(vd: ValDef, sym: Symbol) extends RuleWarning {
      val pos = vd.pos
      val message = "haha failed!"
    }

    val step = optimize {
      case vd: ValDef if vd.name.toString.trim == "bob" =>
        val lookup = lookupContext(vd).lookupSymbol(TermName("titi"), _ => true)
        //nok(Warning(vd, state.lookup(TermName("titi"), _ => true).symbol))
        nok(Warning(vd, lookup.symbol))
    }
  }

  "Scoping traversal" should "work with normal imports" in {
    val tree = fromString("""
      object Toto {
        val titi = 1
        private val titi2 = 2
      }
      class Titi {
        import Toto._
        val tutu = titi // use it or import gets removed!
        val bob = 1
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "work with companion imports" in {
    val tree = fromString("""
      package test
      object Titi {
        val titi2 = 1
        private val titi = 2
      }
      class Titi {
        import Titi._
        val bob = 1
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "work with parent elements" in {
    val tree = fromString("""
      class Titi1 {
        val titi2 = 1
        private val titi = 2
      }
      class Titi2 extends Titi1 {
        val bob = 1
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "work in blocks" in {
    val tree = fromString("""
      class Titi {
        def test = {
          val titi = 1
          val bob = 2
          titi
        }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }

  it should "follow block scoping rules I" in {
    val tree = fromString("""
      class Titi {
        val titi = 2

        def test = {
          val titi = 1
          val bob = 2
          titi
        }
      }
    """)

    global.ask { () =>
      val warnings = apply(rule)(tree)
      assert(warnings.size == 1 && warnings.exists(w => w.sym.fullName == "Titi.titi" && w.sym.owner.isMethod))
    }
  }

  it should "follow block scoping rules II (scoping makes forward reference)" in {
    val tree = fromString("""
      class Titi {
        val titi = 2

        def test = {
          val bob = 2
          val titi = 1
          titi
        }
      }
    """)

    global.ask { () =>
      val warnings = apply(rule)(tree)
      assert(warnings.size == 1 && warnings.exists(w => w.sym.fullName == "Titi.titi" && w.sym.owner.isMethod))
    }
  }

  object rule2 extends {
    val context: ScopingRuleTest.this.context.type = ScopingRuleTest.this.context
  } with ScopingRule {
    import context._
    import context.universe._

    val name = "scoping-rule-2"

    case class Warning(tree: Tree) extends RuleWarning {
      val pos = tree.pos
      val message = "haha failed!"
    }

    val step: PartialFunction[Tree, Unit] = {
      case tt @ TypeTree() if tt.tpe == NoType                     =>
      case tree if tree == noSelfType                              =>
      case tree if context.lookupContext(tree) == NoScopingContext => nok(Warning(tree))
    }
  }

  "Scoping contexts" should "be attributed to all trees in AddressBook.scala" in {
    val tree = fromFile("traversal/AddressBook.scala")

    global.ask { () =>
      val warnings = apply(rule2)(tree)
      warnings.size should be(0)
    }
  }

  it should "be attributed in SimpleInterpreter.scala" in {
    val tree = fromFile("traversal/SimpleInterpreter.scala")

    global.ask { () =>
      val warnings = apply(rule2)(tree)
      warnings.size should be(0)
    }
  }

  def nscTest(file: String): Unit = {
    def urls(classLoader: java.lang.ClassLoader): List[String] = classLoader match {
      case cl: java.net.URLClassLoader => cl.getURLs.toList.map(_.toString)
      case c if c.getParent() != null  => urls(c.getParent())
      case c                           => scala.sys.error("invalid classloader")
    }

    val classpath = urls(java.lang.Thread.currentThread.getContextClassLoader)

    val settings = new scala.tools.nsc.Settings
    settings.usejavacp.value = false
    classpath.distinct.foreach { source =>
      settings.classpath.append(source)
      settings.bootclasspath.append(source)
    }

    trait TestPhase extends scala.tools.nsc.SubComponent {
      import global._

      val ctx = new scala.tools.abide.Context(global) with ScopeProvider

      object rule3 extends {
        val context: ctx.type = ctx
      } with ScopingRule {
        import context._
        import context.universe._

        val name = "scoping-rule-3"

        case class Warning(tree: Tree) extends RuleWarning {
          val pos = tree.pos
          val message = "haha failed!"
        }

        val step: PartialFunction[Tree, Unit] = {
          case tt @ TypeTree() if tt.tpe == NoType                     =>
          case tree if tree == noSelfType                              =>
          case tree if context.lookupContext(tree) == NoScopingContext => nok(Warning(tree))
        }
      }

      val phaseName = "test"
      def newPhase(prev: scala.tools.nsc.Phase): StdPhase = new Phase(prev)
      class Phase(prev: scala.tools.nsc.Phase) extends StdPhase(prev) {
        def apply(unit: CompilationUnit): Unit = {
          rule3.traverse(unit.body.asInstanceOf[rule3.universe.Tree])
          rule3.result.warnings.size should be(0)
        }
      }
    }

    val compiler = new scala.tools.nsc.Global(settings, new scala.tools.nsc.reporters.Reporter {
      def info0(pos: scala.reflect.internal.util.Position, msg: String, severity: Severity, force: Boolean) = ()
    }) { self =>

      object testPhase extends {
        val global: self.type = self
        val runsAfter = List("typer")
        val runsRightAfter = None
      } with TestPhase

      override protected def computeInternalPhases(): Unit = {
        val phs = List(
          syntaxAnalyzer -> "parse source into ASTs, perform simple desugaring",
          analyzer.namerFactory -> "resolve names, attach symbols to named trees",
          analyzer.packageObjects -> "load package objects",
          analyzer.typerFactory -> "the meat and potatoes: type the trees",
          testPhase -> "just here for testing..."
        )
        phs foreach { phasesSet += _._1 }
      }
    }

    val fileURL = getClass.getClassLoader.getResource(file)
    val filePath = new java.io.File(fileURL.toURI).getAbsolutePath
    val cmd = new scala.tools.nsc.CompilerCommand(filePath :: Nil, settings)
    val run = new compiler.Run()
    run compile cmd.files
  }

  it should "also work with nsc.Global on AddressBook.scala" in {
    nscTest("traversal/AddressBook.scala")
  }

  it should "also work with nsc.Global on SimpleInterpreter.scala" in {
    nscTest("traversal/SimpleInterpreter.scala")
  }

}
