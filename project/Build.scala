import sbt._
import Keys._

object AbideBuild extends Build {

  lazy val sharedSettings = Seq(
    organization                    := "com.typesafe",
    version                         := "0.1-SNAPSHOT",
    scalaVersion                    := "2.11.1",
    scalacOptions                  ++= Seq("-deprecation", "-feature"),
    scalaSource in Test            <<= (baseDirectory in Test)(base => base / "test"),
    scalaSource in Compile         <<= (baseDirectory in Compile)(base => base / "src"),
    resourceDirectory in Compile   <<= (baseDirectory in Compile)(base => base / "resources"),
    libraryDependencies            <+= (scalaVersion)("org.scala-lang" % "scala-compiler" % _ % "provided"),
    libraryDependencies            <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _ % "provided"),
    publishArtifact in Test         := false
  )

  lazy val core  = Project("abide-core", file("core")).settings(sharedSettings : _*)

  lazy val abide = Project("abide", file("abide"))
    .settings(sharedSettings : _*)
    .settings(
      mappings in (Compile, packageBin) ++= (mappings in (core, Compile, packageBin)).value,
      mappings in (Compile, packageSrc) ++= (mappings in (core, Compile, packageSrc)).value
    ).dependsOn(core)

  lazy val tests = Project("tests", file("tests"))
    .settings(sharedSettings : _*)
    .settings(
      scalaSource in Test          <<= (baseDirectory in Test)(base => base / "test"),
      resourceDirectory in Test    <<= (baseDirectory in Test)(base => base / "resources"),
      libraryDependencies           += ("org.scalatest" %% "scalatest" % "2.1.7" % "test"),
      testOptions in Test           += Tests.Argument("-oF"),
      packagedArtifacts             := Map.empty
    ).dependsOn(abide)

  lazy val root  = Project("root", file("."))
    .settings(sharedSettings : _*)
    .settings(
      test in Test                  := (test in tests in Test).value,
      packagedArtifacts             := Map.empty
    ).aggregate(core, abide, tests)

}
