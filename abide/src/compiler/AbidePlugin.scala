package scala.tools.abide
package compiler

import scala.tools.nsc._
import scala.tools.nsc.plugins._

class AbidePlugin(val global: Global) extends Plugin {
  import global._

  val name = "abide"
  val description = "static code analysis for Scala"

  val components : List[PluginComponent] = List(component)

  private var analyzer : String = scala.tools.abide.Abide.defaultAnalyzer

  private lazy val abide = scala.tools.abide.Abide.analyzer(global, analyzer).enableAll

  private[abide] object component extends {
    val global : AbidePlugin.this.global.type = AbidePlugin.this.global
  } with PluginComponent {
    val runsAfter = List("typer")
    val phaseName = AbidePlugin.this.name

    def newPhase(prev : Phase) = new StdPhase(prev) {
      override def name = AbidePlugin.this.name

      def apply(unit : CompilationUnit) {
        val warnings = abide(unit.body)
        println(warnings.size)
      }
    }
  }

  override def processOptions(options: List[String], error: String => Unit) {
    for (option <- options) {
      if (option.startsWith("analyzer:")) {
         analyzer = option.substring("analyzer:".length)
       } else {
         scala.sys.error("Option not understood: "+option)
      }
    }
  }
}
