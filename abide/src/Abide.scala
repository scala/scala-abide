package scala.tools.abide

import scala.tools.nsc._
import scala.tools.nsc.plugins._
import scala.tools.nsc.reporters._
import scala.reflect.runtime.{ universe => ru }
import scala.reflect.internal.util._

/** Main class provider for the Abide framework. Useful for creating build-tool plugins. */
object Abide {

  /** Simple compiler that stops after `typer` and registers the Abide compiler plugin ([[compiler.AbidePlugin]]) */
  class AbideCompiler(settings: Settings, reporter: Reporter) extends Global(settings, reporter) {
    lazy val abidePlugin = new scala.tools.abide.compiler.AbidePlugin(this)

    override protected def loadRoughPluginsList: List[Plugin] = {
      abidePlugin :: super.loadRoughPluginsList
    }

    override protected def computeInternalPhases(): Unit = {
      val phs = List(
        syntaxAnalyzer -> "parse source into ASTs, perform simple desugaring",
        analyzer.namerFactory -> "resolve names, attach symbols to named trees",
        analyzer.packageObjects -> "load package objects",
        analyzer.typerFactory -> "the meat and potatoes: type the trees"
      )

      phs foreach { phasesSet += _._1 }
    }
  }

  private lazy val settings = new Settings(println)

  private lazy val reporter = new ConsoleReporter(settings)

  private lazy val compiler = new AbideCompiler(settings, reporter)

  /** Main method for the Abide framework, transparently passes all arguments to the [[AbideCompiler]] instance */
  def main(args: Array[String]): Unit = {
    val command = new CompilerCommand(args.toList, settings)
    val run = new compiler.Run
    run.compile(command.files)
  }

}
