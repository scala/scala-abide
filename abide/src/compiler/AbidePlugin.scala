package scala.tools.abide
package compiler

import scala.tools.nsc._
import scala.tools.nsc.plugins._
import scala.reflect.runtime.{universe => ru}

import presentation._

class AbidePlugin(val global: Global) extends Plugin {
  import global._

  val name = "abide"
  val description = "static code analysis for Scala"

  val components : List[PluginComponent] = List(component)

  lazy val presenter = new presentaion.ConsolePresenter(global)

  private def reflect(className : String) : Any = {
    val mirror = ru.runtimeMirror(getClass.getClassLoader)

    val classSymbol = mirror.staticClass(className)
    val classMirror = mirror.reflectClass(classSymbol)

    val constructorSymbol = classSymbol.typeSignature.member(ru.termNames.CONSTRUCTOR).asMethod
    val constructorMirror = classMirror.reflectConstructor(constructorSymbol)

    constructorMirror(global)
  }

  lazy val mirror = ru.runtimeMirror(getClass.getClassLoader)
  lazy val rules = for (ruleClass <- (ServiceLoader load classOf[Rule]).asScala) yield {
    val ruleSymbol = mirror reflect ruleClass
    val ruleMirror = mirror reflectClass ruleSymbol

    val constructorSymbol = ruleSymbol.typeSignature.member(ru.termNames.CONSTRUCTOR).asMethod
    val constructorMirror = ruleMirror.reflectConstructor(constructorSymbol)

    constructorMirror(global).asInstanceOf[Rule { val global : AbidePlugin.this.global.type }]
  }

//  lazy val analysisComponents = rules.map(

  private[abide] object component extends {
    val global : AbidePlugin.this.global.type = AbidePlugin.this.global
  } with PluginComponent {
    val runsAfter = List("typer")
    val phaseName = AbidePlugin.this.name

    type AnalyzerType = Analyzer { val global : AbidePlugin.this.global.type }
    lazy val analyzer = reflect(analyzerClass).asInstanceOf[AnalyzerType].enableAll

    type PresenterType = Presenter { val global : AbidePlugin.this.global.type }
    lazy val presenter = reflect(presenterClass).asInstanceOf[PresenterType]

    def newPhase(prev : Phase) = new StdPhase(prev) {
      override def name = AbidePlugin.this.name

      def apply(unit : CompilationUnit) {
        val warnings = analyzer(unit.body)
        presenter(unit, warnings)
      }
    }
  }

  override def processOptions(options: List[String], error: String => Unit) {
    for (option <- options) {
      if (option.startsWith("analyzer:")) {
         analyzerClass = option.substring("analyzer:".length)
       } else {
         scala.sys.error("Option not understood: "+option)
      }
    }
  }
}
