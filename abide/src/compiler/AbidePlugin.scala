package scala.tools.abide.compiler

import scala.tools.nsc._
import scala.tools.nsc.plugins._
import scala.reflect.runtime.{universe => ru}

import scala.tools.abide._
import scala.tools.abide.presentation._

/**
 * AbidePlugin
 *
 * Compiler plugin for running the Abide framework. The plugin runs right after `typer` and uses
 * a series of rules and analyzers to perform actual verification. It then uses a [[presentation.Presenter]] to
 * output the result of the verification.
 *
 * The plugin accepts a series of abide-specific arguments (use as -P:abide:argName:argValue) :
 *
 *  - ruleClass :
 *      Specifies the full name of each rule that should be handled by the plugin (eg. com.typesafe.abide.samples.UnusedMember)
 *      This option can (and should) appear multiple times in the arguments array to specify all rules we're dealing with.
 *
 *  - analyzerClass :
 *      Specifies the full name of all analyzer generator objects that can be used to instantiate rules. In practice, since the
 *      analyzer generator is actually a member of the rule, this argument can be omitted. However, the subsumption mechanism
 *      (see [[AnalyzerGenerator]]) enables users to provide more powerful analyzers that will replace the default analyzer
 *      statically specified in the class description. Such extension analyzers _must_ appear in an `analyzerClass` argument.
 *      As in ruleClass, analyzerClass can (and generally should) appear multiple times in the arguments array.
 *
 *  - abidecp :
 *      Abide can split up rule sets into multiple jars, so when using Abide in compiler plugin mode, these jars need to be
 *      specified to the compiler so it can actually load the rules. Therefore, we provide the `abidecp` option that must consist
 *      in a colon-separated list of classpath entries pointing to rule locations.
 *
 * Note that unlike analyzers, rule context cannot be overriden by arguments and must be specified in the rule's companion object,
 * since configurations are custom tailored to single rules (for more information, see [[ContextGenerator]]).
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

  private lazy val analyzerGenerators = for (analyzerClass <- analyzerClasses) yield {
    val analyzerSymbol = mirror staticModule analyzerClass
    val analyzerMirror = mirror reflectModule analyzerSymbol
    analyzerMirror.instance.asInstanceOf[AnalyzerGenerator]
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
        val context = generator.getContext(global)
        val bottomCtx = list.foldLeft(context) { case (acc, (rule, ctx)) => generalize(acc, ctx) }
        (rule -> bottomCtx) :: (list map { case (rule, ctx) => rule -> generalize(ctx, bottomCtx) })
    }.toMap
  }

  private lazy val rules = for (rule @ (ruleSymbol, ruleMirror) <- ruleMirrors) yield {
    val constructorSymbol = ruleSymbol.typeSignature.member(ru.termNames.CONSTRUCTOR).asMethod
    val constructorMirror = ruleMirror reflectConstructor constructorSymbol
    val context = ruleContexts(rule)

    constructorMirror(context).asInstanceOf[Rule { val context : Context { val universe : AbidePlugin.this.global.type } }]
  }

  private lazy val analyzers : List[(Rule => Boolean) => Analyzer { val global : AbidePlugin.this.global.type }] = {
    def generalize(g1 : AnalyzerGenerator, g2 : AnalyzerGenerator) : AnalyzerGenerator = {
      def fix[A](a : A)(f : A => A) : A = { val na = f(a); if (na == a) na else fix(na)(f) }
      val g2Subsumes : Set[AnalyzerGenerator] = fix(g2.subsumes)(set => set ++ set.flatMap(_.subsumes))
      if (g2Subsumes(g1)) g2 else g1
    }

    val allGenerators : List[(Rule, AnalyzerGenerator)] = rules.map(rule => rule -> rule.analyzer)
    val ruleToGenerator = allGenerators.foldLeft(List.empty[(Rule, AnalyzerGenerator)]) { case (list, (rule, generator)) =>
      val generalized = analyzerGenerators.foldLeft(generator)((acc, gen) => generalize(gen, acc))
      val bottomGen = list.foldLeft(generalized) { case (acc, (rule, generator)) => generalize(generator, acc) }
      (rule -> bottomGen) :: (list map { case (rule, gen) => rule -> generalize(gen, bottomGen) })
    }

    ruleToGenerator.groupBy(_._2).toList.map { case (generator, rulePairs) =>
      val rules = rulePairs.map(_._1)
      (filter : Rule => Boolean) => {
        val analyzer = generator.getAnalyzer(global, rules.filter(filter))
        analyzer.asInstanceOf[Analyzer { val global : AbidePlugin.this.global.type }]
      }
    }
  }

  private lazy val presenter = new presentation.ConsolePresenter(global).asInstanceOf[Presenter { val global : AbidePlugin.this.global.type }]

  private[abide] object component extends {
    val global : AbidePlugin.this.global.type = AbidePlugin.this.global
  } with PluginComponent {
    val runsAfter = List("typer")
    val phaseName = AbidePlugin.this.name

    def newPhase(prev : Phase) = new StdPhase(prev) {
      override def name = AbidePlugin.this.name

      def apply(unit : CompilationUnit): Unit = {
        val warnings = analyzers.flatMap {
          gen => gen(_ => true)(unit.body)
        }

        presenter(unit, warnings)
      }
    }
  }

  private var abideCp : String = ""
  private var ruleClasses : List[String] = Nil
  private var analyzerClasses : List[String] = Nil

  override def processOptions(options: List[String], error: String => Unit): Unit = {
    for (option <- options) {
      if (option.startsWith("ruleClass:")) {
        ruleClasses ::= option.substring("ruleClass:".length)
      } else if (option.startsWith("analyzerClass:")) {
        analyzerClasses ::= option.substring("analyzerClass:".length)
      } else if (option.startsWith("abidecp:")) {
        abideCp = option.substring("abidecp:".length)
      } else {
        scala.sys.error("Option not understood: "+option)
      }
    }
  }
}
