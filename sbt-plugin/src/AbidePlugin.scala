package scala.tools.abide

import sbt._
import Keys._
import scala.language.reflectiveCalls
import scala.reflect.runtime.{universe => ru}

object AbidePlugin extends AutoPlugin {

  lazy val AbideConfig = config("abide").hide
  lazy val AbideCompile = config("abide-compile").extend(Compile).hide

  object autoImport {
    val analyzer = settingKey[String]("User-defined analyzer class")
    val abide = taskKey[Unit]("Runs abide verification on current project")

    val abideCommand : Command = Command.command("abide-tmp") { state : State =>
      val extracted = Project extract state
      import extracted.{ showKey, structure, currentRef }
      import Aggregation._
      import EvaluateTask._

      val taskKeys = Seq(
        compile in Global in Compile      in currentRef,
        compile in Global in AbideCompile in currentRef
      )

      val newState = taskKeys.foldLeft(state) { (state, taskKey) => 
        val keys = Act.keyValues(structure)(Seq(taskKey.scopedKey)).toList
        val (tasks, settings) = keys.partition {
          case KeyValue(k, v : Task[_]) => true
          case _ => false
        }

        val toRun = tasks.map { case KeyValue(k, t) => t.map(v => KeyValue(k, v)) }.join
        val roots = tasks.map { case KeyValue(k, _) => k }
        val config = extractedTaskConfig(extracted, structure, state)

        val (newState, result) = withStreams(structure, state) { streams =>
          val transform = nodeView(state, streams, roots)
          runTask(toRun, state, streams, structure.index.triggers, config)(transform)
        }

        val ext = Project extract newState
        val lines = Output.lastLines(keys, ext.structure.streams(newState), Some(CommandStrings.ExportStream))
        val executables = lines.flatMap { case KeyValue(_, v) => v }
        println(executables)

        newState
      }

     // , Select(ext.currentRef), ext.showKey

//      println(extractLast(newState))

      newState

      /*
      val toRun = tasks.map { case KeyValue(k, t) => t.map(v => KeyValue(k, v)) }.join
      Project runTask (toRun, state) map { case (newState, result) =>
        println(result)
        newState
      }.getOrElse(state)
      */
    }

    private[AbidePlugin] lazy val abideCommandSettings : Seq[sbt.Def.Setting[_]] = Seq(
      commands += abideCommand,
      abide := {
        val cp : Seq[java.io.File] = (dependencyClasspath in Compile).value.files
        val cpString : String = cp.map(_.getAbsolutePath).mkString(java.io.File.pathSeparatorChar.toString)
        val sp : Seq[String] = (sources in Compile).value.map(_.getAbsolutePath)
        val className : String = (analyzer in abide).value
        val options = "-classpath" +: cpString +: ("-P:abide:analyzer:" + className) +: sp

        val abideCp : Seq[java.io.File] = update.value.select(configurationFilter("abide"))
        val loader : ClassLoader = sbt.classpath.ClasspathUtilities.toLoader(abideCp)

        val mirror = ru.runtimeMirror(loader)
        val objectSymbol = mirror.staticModule("scala.tools.abide.Abide")
        val abideObj = mirror.reflectModule(objectSymbol).instance.asInstanceOf[{ def main(args : Array[String]) : Unit }]
        abideObj.main(options.toArray)
      },
      analyzer in abide := "scala.tools.abide.DefaultAnalyzer"
    )
  }

  import autoImport._

  override def requires = sbt.plugins.JvmPlugin

  override def trigger = allRequirements

  private def abideScalaVersion(version : String) : String = CrossVersion.partialVersion(version) match {
    case Some((2, scalaMajor)) if scalaMajor >= 11 => version
    case Some((2, 10)) => "2.11.1"
  }

  override def projectSettings = super.projectSettings ++ Seq(
    ivyConfigurations   ++= Seq(AbideConfig, AbideCompile),
    compile in AbideCompile <<= compile in Compile,
    scalacOptions in AbideCompile := Seq.empty,
    scalaVersion         in AbideConfig  := abideScalaVersion((scalaVersion in Compile).value),
    scalaVersion         in AbideCompile := abideScalaVersion((scalaVersion in Compile).value),
    managedScalaInstance in AbideCompile := false,
    libraryDependencies  += ("com.typesafe" %% "abide" % "0.1-SNAPSHOT" % "abide"),
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-compiler" % _ % "abide;abide-compile"),
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-library" % _ % "abide;abide-compile"),
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _ % "abide")
  ) ++ abideCommandSettings

}
