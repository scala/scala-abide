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
    testOptions       in Test     += Tests.Argument("-oF"),
    scalaSource       in Compile <<= (baseDirectory in Compile)(_ / "src"),
    resourceDirectory in Compile <<= (baseDirectory in Compile)(_ / "resources"),
    scalaSource       in Test    <<= (baseDirectory in Test)(_ / "test"),
    resourceDirectory in Test    <<= (baseDirectory in Test)(_ / "test-resources"),
    libraryDependencies          <+= (scalaVersion)("org.scala-lang" % "scala-compiler" % _ % "provided"),
    libraryDependencies          <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _ % "provided"),
    libraryDependencies           += "org.scalatest" %% "scalatest" % "2.1.7" % "test",
    publishArtifact in Test       := false
  )

  lazy val macros = Project("abide-macros", file("macros")).settings(sharedSettings : _*)

  lazy val abide = Project("abide", file("abide"))
    .settings(sharedSettings : _*)
    .settings(
      mappings in (Compile, packageBin) ++= (mappings in (macros, Compile, packageBin)).value,
      mappings in (Compile, packageSrc) ++= (mappings in (macros, Compile, packageSrc)).value
    ).dependsOn(macros % "compile->compile;test->test")

  lazy val sbt = Project("sbt-abide", file("sbt-plugin"))
    .settings(sharedSettings : _*)
    .settings(
      sbtPlugin    := true,
      scalaVersion := "2.10.4"
    )

  lazy val sampleRules = Project("abide-samples", file("rules/samples"))
    .settings(sharedSettings : _*)
    .dependsOn(abide % "compile->compile;test->test")

  lazy val akkaRules = Project("abide-akka", file("rules/akka"))
    .settings(sharedSettings : _*)
    .settings(libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.3" % "test")
    .dependsOn(abide % "compile->compile;test->test")

  lazy val rules = Seq(sampleRules, akkaRules)

  lazy val allProjects = Seq(macros, abide, sbt) ++ rules

  lazy val filter = ScopeFilter(inAggregates(ThisProject, includeRoot=false))

  lazy val root = (Project("root", file("."))
    .settings(
      //test in Test <<= allProjects.map(p => test in Test in p).dependOn,
      //test in Test      := (test in tests in Test).value,
      //parallelExecution in Global := false,
      packagedArtifacts := Map.empty
    ) /: allProjects) (_ aggregate _)

}
