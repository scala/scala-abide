import sbt._
import Keys._

object AbideBuild extends Build {

  lazy val abide = Project("abide-core", file("src/abide")).settings(
    scalaVersion := "2.11.0",
    // crossScalaVersions := Seq("2.10.4", "2.11.0"),
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-compiler" % _),
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _),
    libraryDependencies += ("org.scalatest" %% "scalatest" % "2.1.7" % "test"
      excludeAll(ExclusionRule(organization="org.scala-lang"))),
    libraryDependencies := {
      CrossVersion.partialVersion(scalaVersion.value) match {
        // if scala 2.11+ is used, quasiquotes are merged into scala-reflect
        case Some((2, scalaMajor)) if scalaMajor >= 11 => libraryDependencies.value

        // in Scala 2.10, quasiquotes are provided by macro paradise
        case Some((2, 10)) => libraryDependencies.value ++ Seq(
          compilerPlugin("org.scalamacros" % "paradise" % "2.0.0" cross CrossVersion.full),
          "org.scalamacros" %% "quasiquotes" % "2.0.0" cross CrossVersion.binary
        )
      }
    },
    scalacOptions ++= Seq("-deprecation", "-feature"),
    testOptions in Test += Tests.Argument("-oF")
  )

  lazy val rules = Project("abide-rules", file("src/rules")).settings(
    scalaVersion := "2.11.0",
    scalacOptions ++= Seq("-deprecation", "-feature"),
    testOptions in Test += Tests.Argument("-oF")
  ).dependsOn(abide)

  lazy val root = Project("abide", file("src")).aggregate(abide, rules)
}
