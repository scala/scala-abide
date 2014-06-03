package scala.tools.abide

import scala.tools.nsc.io._
import scala.tools.nsc.interactive._
import scala.tools.nsc.reporters._
import scala.reflect.internal.util._
import org.scalatest._

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

    val compiler = new Global(settings, new ConsoleReporter(settings))

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

trait TreeProvider extends CompilerProvider {

  val randomFileName = {
    val r = new java.util.Random
    () => "/tmp/abideInput" + r.nextInt + ".scala"
  }

  def fromSource(source: SourceFile) : global.Tree = {
    val response = new global.Response[global.Tree]

    global.ask { () =>
      global.askLoadedTyped(source, true, response)
    }

    response.get match {
      case Left(tree) => tree
      case Right(ex) => throw ex
    }
  }

  def fromFile(fileName: String) : global.Tree = {
    val fileURL = getClass.getClassLoader.getResource(fileName)
    val filePath = new java.io.File(fileURL.toURI).getAbsolutePath
    val source = new BatchSourceFile(AbstractFile.getFile(filePath))
    fromSource(source)
  }

  def fromFolder(folderName : String) = {
    import java.io.File
    import scala.collection.JavaConversions._

    def getFileTree(f: File): Stream[File] =
      f #:: (if (f.isDirectory) f.listFiles().toStream.flatMap(getFileTree) else Stream.empty)

    val folder = new File(folderName)
    val files = getFileTree(folder).filter(file => file.isFile && file.getName.endsWith(".scala"))
    files.map(f => fromSource(global.getSourceFile(f.getAbsolutePath)))
  }

  def fromString(str: String) : global.Tree = {
    val response = new Response[global.Tree]
    val fileName = randomFileName()
    val file = new BatchSourceFile(fileName, str)
    fromSource(file)
  }

  def classByName(str: String) : global.ClassSymbol = {
    global.rootMirror.getClassByName(global.newTypeName(str))
  }

  def objectByName(str: String) : global.ModuleSymbol = {
    global.rootMirror.getModuleByName(global.newTermName(str))
  }

}
