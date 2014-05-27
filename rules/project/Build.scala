import sbt._

object RootBuild extends Build {

  lazy val library = RootProject(file("../core"))

  lazy val root = Project(id = "abide-rules", base = file("."))
    .dependsOn(library % "test->test;compile->compile")
}
