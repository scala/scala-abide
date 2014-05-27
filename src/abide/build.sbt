name := "abide-core"

version := "1.0"

scalaVersion := "2.11.0"

moduleName := "scala.tools.abide"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-compiler" % "2.11.0",
  "org.scala-lang" % "scala-reflect" % "2.11.0",
  "org.scalatest" % "scalatest_2.11" % "2.1.5" % "test" excludeAll(ExclusionRule(organization="org.scala-lang"))
)

scalacOptions ++= Seq("-deprecation", "-feature")

testOptions in Test += Tests.Argument("-oF")
