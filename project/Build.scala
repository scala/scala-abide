import sbt._
import Keys._

object AbideBuild extends Build {

  lazy val abideSettings = Seq(
    organization := "com.typesafe",
    version      := "0.1-SNAPSHOT"
  )

  lazy val sharedSettings = abideSettings ++ Seq(
    scalaVersion                  := "2.11.1",
    scalacOptions                ++= Seq("-deprecation", "-feature"),
    scalaSource       in Compile <<= (baseDirectory in Compile)(_ / "src"),
    resourceDirectory in Compile <<= (baseDirectory in Compile)(_ / "resources"),
    libraryDependencies          <+= (scalaVersion)("org.scala-lang" % "scala-compiler" % _ % "provided"),
    libraryDependencies          <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _ % "provided"),
    publishArtifact in Test       := false
  )

  lazy val macros = Project("abide-macros", file("macros")).settings(sharedSettings : _*)

  lazy val abide = Project("abide", file("abide"))
    .settings(sharedSettings : _*)
    .settings(
      mappings in (Compile, packageBin) ++= (mappings in (macros, Compile, packageBin)).value,
      mappings in (Compile, packageSrc) ++= (mappings in (macros, Compile, packageSrc)).value
    ).dependsOn(macros)

  lazy val sbt = Project("sbt-abide", file("sbt-plugin"))
    .settings(sharedSettings : _*)
    .settings(
      sbtPlugin    := true,
      scalaVersion := "2.10.4"
    )

  lazy val tests = Project("tests", file("tests"))
    .settings(sharedSettings : _*)
    .settings(
      scalaSource       in Test <<= (baseDirectory in Test)(_ / "test"),
      resourceDirectory in Test <<= (baseDirectory in Test)(_ / "resources"),
      testOptions       in Test  += Tests.Argument("-oF"),
      libraryDependencies        += "org.scalatest" %% "scalatest" % "2.1.7" % "test",
      packagedArtifacts          := Map.empty
    ).dependsOn(macros, abide)

  lazy val root = Project("root", file("."))
    .settings(
      test in Test      := (test in tests in Test).value,
      packagedArtifacts := Map.empty
    ).aggregate(macros, abide, tests, sbt)

}
