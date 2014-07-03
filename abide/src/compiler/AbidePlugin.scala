package scala.tools.abide.compiler

import scala.tools.nsc._
import scala.tools.nsc.plugins._
import scala.reflect.runtime.{universe => ru}

import scala.tools.abide._
import scala.tools.abide.presentation._

/**
 * Compiler plugin for running the Abide framework. The plugin runs right after `typer` and uses
 * a series of rules and analyzers to perform actual verification. It then uses a [[presentation.Presenter]] to
 * output the result of the verification.
 *
 * Rules should be specified to the plugin using the -P:abide:ruleClass:"path.to.rule.Class" option. This option
 * can (and should) appear multiple times to specify all the rules the plugin should deal with. Each rule will
 * then be loaded from classpath and examined to handle context and analyzer generation.
 *
 * @see [[scala.tools.abide.ContextGenerator]]
 * @see [[scala.tools.abide.AnalyzerGenerator]]
 */
class AbidePlugin(val global: Global) extends Plugin {
  import global._

  val name = "abide"
  val description = "static code analysis for Scala"

  val components : List[PluginComponent] = List(component)

  private lazy val classLoader = new java.net.URLClassLoader(
    abideCp.split(":").filter(_ != "").map(f => new java.io.File(f).toURI.toURL),
    getClass.getClassLoader)

  private lazy val mirror = ru.runtimeMirror(classLoader)
  private lazy val ruleMirrors = for (ruleClass <- ruleClasses) yield {
    val ruleSymbol = mirror staticClass ruleClass
    val ruleMirror = mirror reflectClass ruleSymbol

    ruleSymbol -> ruleMirror
  }

  private lazy val ruleContexts = {
    import ru._

    def contextGenerator(sym : ru.Symbol) : ru.ModuleSymbol = sym.asClass.baseClasses.collectFirst {
      case tpe : ru.TypeSymbol if tpe.toType.companion <:< ru.typeOf[ContextGenerator] =>
        tpe.toType.companion.typeSymbol.asClass.module.asModule
    }.get

    val contextGenerators = for (rule @ (ruleSymbol, ruleMirror) <- ruleMirrors) yield {
      val generatorSymbol = contextGenerator(ruleSymbol)
      val generatorMirror = mirror reflectModule generatorSymbol

      rule -> generatorMirror.instance.asInstanceOf[ContextGenerator]
    }

    def typeTag[T : ru.TypeTag](obj : T) : ru.TypeTag[T] = ru.typeTag[T]
    def generalize[T1 <: Context : ru.TypeTag, T2 <: Context : ru.TypeTag](o1 : T1, o2 : T2) : Context = {
      if (typeTag(o1).tpe <:< typeTag(o2).tpe) o1 else o2
    }

    contextGenerators.foldLeft(List.empty[((ru.ClassSymbol, ru.ClassMirror), Context)]) {
      case (list, (rule @ (ruleSymbol, ruleMirror), generator)) =>
        val context = generator.mkContext(global)
        val bottomCtx = list.foldLeft(context) { case (acc, (rule, ctx)) => generalize(acc, ctx) }
        (rule -> bottomCtx) :: (list map { case (rule, ctx) => rule -> generalize(ctx, bottomCtx) })
    }.toMap
  }

  private lazy val rules = for (rule @ (ruleSymbol, ruleMirror) <- ruleMirrors) yield {
    val constructorSymbol = ruleSymbol.typeSignature.member(ru.termNames.CONSTRUCTOR).asMethod
    val constructorMirror = ruleMirror reflectConstructor constructorSymbol
    val context = ruleContexts(rule)

    constructorMirror(context).asInstanceOf[Rule { val context : Context { val global : AbidePlugin.this.global.type } }]
  }

  private lazy val ruleAnalyzers = {
    def generalize(g1 : AnalyzerGenerator, g2 : AnalyzerGenerator) : AnalyzerGenerator = {
      def fix[A](a : A)(f : A => A) : A = { val na = f(a); if (na == a) na else fix(na)(f) }
      val g2Subsumes : Set[AnalyzerGenerator] = fix(g2.subsumes)(set => set ++ set.flatMap(_.subsumes))
      if (g2Subsumes(g1)) g2 else g1
    }

    val allGenerators : List[(Rule, AnalyzerGenerator)] = rules.map(rule => rule -> rule.analyzer)
    allGenerators.foldLeft(List.empty[(Rule, AnalyzerGenerator)]) { case (list, (rule, generator)) =>
      val bottomGen = list.foldLeft(generator) { case (acc, (rule, generator)) => generalize(generator, acc) }
      (rule -> bottomGen) :: (list map { case (rule, gen) => rule -> generalize(gen, bottomGen) })
    }
  }

  private lazy val analyzers = ruleAnalyzers.groupBy(_._2).toList.map { case (generator, rules) =>
    generator.mkAnalyzer(global, rules.map(_._1)).asInstanceOf[Analyzer { val global : AbidePlugin.this.global.type }]
  }

  private lazy val presenter = new presentation.ConsolePresenter(global).asInstanceOf[Presenter { val global : AbidePlugin.this.global.type }]

  private[abide] object component extends {
    val global : AbidePlugin.this.global.type = AbidePlugin.this.global
  } with PluginComponent {
    val runsAfter = List("typer")
    val phaseName = AbidePlugin.this.name

    def newPhase(prev : Phase) = new StdPhase(prev) {
      override def name = AbidePlugin.this.name

      private var time : Long = 0

      def apply(unit : CompilationUnit) {
        val millis = System.currentTimeMillis
        val warnings = analyzers.flatMap(analyzer => analyzer(unit.body))
        time += System.currentTimeMillis - millis
        println("time="+time)
        presenter(unit, warnings)
      }
    }
  }

  private var abideCp : String = ""
  private var ruleClasses : List[String] = Nil

  override def processOptions(options: List[String], error: String => Unit) {
    for (option <- options) {
      if (option.startsWith("ruleClass:")) {
        ruleClasses ::= option.substring("ruleClass:".length)
      } else if (option.startsWith("abidecp:")) {
        println(option)
        abideCp = option.substring("abidecp:".length)
      } else {
        scala.sys.error("Option not understood: "+option)
      }
    }
  }
}
