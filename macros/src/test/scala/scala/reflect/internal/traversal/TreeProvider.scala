package scala.reflect.internal.traversal

import scala.tools.nsc.io._
import scala.tools.nsc.interactive._
import scala.reflect.internal.util._

/**
 * trait TreeProvider
 *
 * Trait that provides access to trees given strings, file names, etc and also provides a few
 * convenience methods on these trees. Mostly useful for writing tests.
 */
trait TreeProvider extends CompilerProvider {

  private val randomFileName = {
    val r = new java.util.Random
    () => "/tmp/abideInput" + r.nextInt + ".scala"
  }

  def fromSource(source: SourceFile): global.Tree = {
    val response = new global.Response[global.Tree]

    global.ask { () =>
      global.askLoadedTyped(source, true, response)
    }

    response.get match {
      case Left(tree) => tree
      case Right(ex)  => throw ex
    }
  }

  def fromFile(fileName: String): global.Tree = {
    val fileURL = getClass.getClassLoader.getResource(fileName)
    val filePath = new java.io.File(fileURL.toURI).getAbsolutePath
    val source = new BatchSourceFile(AbstractFile.getFile(filePath))
    fromSource(source)
  }

  def fromFolder(folderName: String) = {
    import java.io.File
    import scala.collection.JavaConversions._

    def getFileTree(f: File): Stream[File] =
      f #:: (if (f.isDirectory) f.listFiles().toStream.flatMap(getFileTree) else Stream.empty)

    val folder = new File(folderName)
    val files = getFileTree(folder).filter(file => file.isFile && file.getName.endsWith(".scala"))
    files.map(f => fromSource(global.getSourceFile(f.getAbsolutePath)))
  }

  def fromString(str: String): global.Tree = {
    val response = new Response[global.Tree]
    val fileName = randomFileName()
    val file = new BatchSourceFile(fileName, str)
    fromSource(file)
  }

  def classByName(tree: global.Tree, str: String): global.Symbol = {
    def rec(tree: global.Tree): Option[global.Symbol] = tree match {
      case cd @ global.ClassDef(_, name, tparams, impl) if name.toString == str => Some(cd.symbol)
      case md @ global.ModuleDef(_, name, impl) if name.toString == str => Some(md.symbol)
      case _ => tree.children.flatMap(rec(_)).headOption
    }

    rec(tree).get
  }

  def memberByName(tree: global.Tree, str: String): global.Tree = {
    def rec(tree: global.Tree): Option[global.Tree] = tree match {
      case vd @ global.ValDef(_, name, _, _)       => Some(vd)
      case dd @ global.DefDef(_, name, _, _, _, _) => Some(dd)
      case _                                       => tree.children.flatMap(rec(_)).headOption
    }

    rec(tree).get
  }

}
