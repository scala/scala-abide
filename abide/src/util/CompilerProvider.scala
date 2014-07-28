package scala.tools.abide.util

import scala.tools.nsc.io._
import scala.tools.nsc.interactive._
import scala.tools.nsc.reporters._
import scala.reflect.internal.util._

/**
 * trait CompilerProvider
 *
 * Trait that provides a simple presentation compiler, mostly used for writing tests.
 */
trait CompilerProvider {

  lazy val global : Global = {

    def urls(classLoader: java.lang.ClassLoader): List[String] = classLoader match {
      case cl: java.net.URLClassLoader => cl.getURLs.toList.map(_.toString)
      case c if c.getParent() != null => urls(c.getParent())
      case c => scala.sys.error("invalid classloader")
    }

    val classpath = urls(java.lang.Thread.currentThread.getContextClassLoader)

    val settings = new scala.tools.nsc.Settings
    settings.usejavacp.value = false
    (classpath :+ "/Users/nvoirol/scala/src/library").distinct.foreach { source =>
      settings.classpath.append(source)
      settings.bootclasspath.append(source)
    }

    val compiler = new Global(settings, new Reporter {
      def info0(pos : Position, msg : String, severity : Severity, force : Boolean) : Unit = ()
    })

    try {
      compiler.ask { () =>
        new compiler.Run
      }
    } catch {
      case e: scala.reflect.internal.MissingRequirementError =>
        val msg = s"""Could not initialize the compiler!
        |  ${settings.userSetSettings.mkString("\n  ")}
        |  ${settings.classpath}
        |  ${settings.bootclasspath}
        |  ${settings.javabootclasspath}""".stripMargin
        throw new Exception(msg, e)
    }

    compiler
  }
}
