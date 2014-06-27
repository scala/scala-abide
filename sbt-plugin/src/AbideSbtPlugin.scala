package scala.tools.abide

import sbt._
import Keys._
import scala.language.reflectiveCalls
import scala.reflect.runtime.{universe => ru}

object AbideSbtPlugin extends AutoPlugin {

  object autoImport {
    val analyzer = settingKey[String]("User-defined analyzer class")
    val abide = taskKey[Unit]("Runs abide verification on current project")
  }

  import autoImport._

  private val AbideConfig = config("abide")

  private def abideScalaVersion(version : String) : String = CrossVersion.partialVersion(version) match {
    case Some((2, scalaMajor)) if scalaMajor >= 11 => version
    case Some((2, 10)) => "2.11.1"
  }

  private def abideBinaryVersion(version : String) : String = CrossVersion.partialVersion(version) match {
    case Some((2, scalaMajor)) if scalaMajor >= 11 => "abide_2." + scalaMajor
    case Some((2, 10)) => "abide_2.11"
  }

  private lazy val abideSettings : Seq[sbt.Def.Setting[_]] = Seq(
    ivyConfigurations += AbideConfig,
    libraryDependencies ++= Seq(
      "com.typesafe" % abideBinaryVersion((scalaVersion in Compile).value) % "0.1-SNAPSHOT" % AbideConfig.name,
      "org.scala-lang" % "scala-compiler" % abideScalaVersion((scalaVersion in Compile).value) % AbideConfig.name,
      "org.scala-lang" % "scala-library" % abideScalaVersion((scalaVersion in Compile).value) % AbideConfig.name,
      "org.scala-lang" % "scala-reflect" % abideScalaVersion((scalaVersion in Compile).value) % AbideConfig.name
    ),
    abide := {
      streams.value.log.info("Running abide in " + thisProject.value.id + " ...")

      val cpOpts : Seq[String] = {
        val deps = (dependencyClasspath in Compile).value
        val cpString = deps.files.map(_.getAbsolutePath).mkString(java.io.File.pathSeparatorChar.toString)
        Seq("-classpath", cpString)
      }

      val analyzerOpts : Seq[String] = Seq("-P:abide:analyzer:" + (analyzer in abide).value)

      val compatibilityOpts : Seq[String] = CrossVersion.partialVersion((scalaVersion in Compile).value) match {
        case Some((2, 10)) => Seq("-Xsource:2.10", "-Ymacro-expand:none")
        case _ => Seq.empty
      }

      val sourcePaths : Seq[String] = (sources in Compile).value.map(_.getAbsolutePath)

      val options = cpOpts ++ analyzerOpts ++ compatibilityOpts ++ sourcePaths

      if (sourcePaths.filter(_.endsWith(".scala")).nonEmpty) {
        val abideCp : Seq[java.io.File] = update.value.select(configurationFilter("abide"))
        val loader : ClassLoader = sbt.classpath.ClasspathUtilities.toLoader(abideCp)

        val mirror = ru.runtimeMirror(loader)
        val objectSymbol = mirror.staticModule("scala.tools.abide.Abide")
        val abideObj = mirror.reflectModule(objectSymbol).instance.asInstanceOf[{ def main(args : Array[String]) : Unit }]
        abideObj.main(options.toArray)
        streams.value.log.info("Done verifying.")
      } else {
        streams.value.log.info("No scala sources : skipping project.")
      }
    },
    analyzer in abide := "scala.tools.abide.DefaultAnalyzer"
  )

  override def requires = sbt.plugins.JvmPlugin

  override def trigger = allRequirements

  override def projectSettings = super.projectSettings ++ abideSettings

}
