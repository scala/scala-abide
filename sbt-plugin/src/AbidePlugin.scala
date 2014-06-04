package scala.tools.abide

import sbt._
import Keys._
import scala.language.reflectiveCalls
import scala.reflect.runtime.{universe => ru}

object AbidePlugin extends AutoPlugin {

  lazy val AbideConfig = config("abide").hide

  object autoImport {
    val analyzer = settingKey[String]("User-defined analyzer class")
    val abide = taskKey[Unit]("Runs abide verification on current project")

    private[AbidePlugin] lazy val abideCommandSettings : Seq[sbt.Def.Setting[_]] = Seq(
      abide := {
        val cp : Seq[java.io.File] = (dependencyClasspath in Compile).value.files
        val cpString : String = cp.map(_.getAbsolutePath).mkString(java.io.File.pathSeparatorChar.toString)
        val sp : Seq[String] = (sources in Compile).value.map(_.getAbsolutePath)
        val className : String = (analyzer in abide).value
        val options = "-cp" +: cpString +: ("-P:abide:analyzer:" + className) +: sp

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

  override def projectSettings = super.projectSettings ++ Seq(
    ivyConfigurations    += AbideConfig,
    libraryDependencies  += ("com.typesafe" %% "abide" % "0.1-SNAPSHOT" % "abide"),
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-compiler" % _ % "abide"),
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-library" % _ % "abide"),
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _ % "abide")
  ) ++ abideCommandSettings

}
