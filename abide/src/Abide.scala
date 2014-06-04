package scala.tools.abide

import scala.tools.nsc._
import scala.tools.nsc.plugins._
import scala.tools.nsc.reporters._
import scala.reflect.runtime.{universe => ru}

object Abide {

  lazy val defaultAnalyzer = "scala.tools.abide.DefaultAnalyzer"

  def analyzer(global : Global, className : String) : Analyzer = {
    val mirror = ru.runtimeMirror(getClass.getClassLoader)

    val classSymbol = mirror.staticClass(className)
    val classMirror = mirror.reflectClass(classSymbol)

    val constructorSymbol = classSymbol.typeSignature.member(ru.termNames.CONSTRUCTOR).asMethod
    val constructorMirror = classMirror.reflectConstructor(constructorSymbol)

    constructorMirror(global).asInstanceOf[Analyzer]
  }

  def analyzer(global : Global) : Analyzer = analyzer(global, defaultAnalyzer)

  class AbideCompiler(settings : Settings, reporter : Reporter) extends Global(settings, reporter) {

    override protected def loadRoughPluginsList : List[Plugin] = {
      new compiler.AbidePlugin(this) :: super.loadRoughPluginsList
    }

    override protected def computeInternalPhases() : Unit = {
      val phs = List(
        syntaxAnalyzer          -> "parse source into ASTs, perform simple desugaring",
        analyzer.namerFactory   -> "resolve names, attach symbols to named trees",
        analyzer.packageObjects -> "load package objects",
        analyzer.typerFactory   -> "the meat and potatoes: type the trees"
      )

      phs foreach { phasesSet += _._1 }
    }
  }

//  def run(options : String) {
  def main(args : Array[String]) {
    val settings = new Settings(println)

    val command = new CompilerCommand(args.toList, settings)

    val reporter = new ConsoleReporter(settings)

    val compiler = new AbideCompiler(settings, reporter)
    val run = new compiler.Run
    run.compile(command.files)
  }

}
