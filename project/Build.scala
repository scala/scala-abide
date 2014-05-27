import sbt._

object AbideBuild extends Build {
  lazy val root = Project(id = "root", base = file(".")).aggregate(core, rules)

  lazy val core = Project(id = "core", base = file("core"))

  lazy val rules = Project(id = "rules", base = file("rules")).dependsOn(core)
}
