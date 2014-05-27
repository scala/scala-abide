import sbt._

object RootBuild extends Build {

  lazy val abide = RootProject(file("../abide"))

  lazy val root = Project(id = "abide-rules", base = file("."))
    .dependsOn(abide % "test->test;compile->compile")
}
