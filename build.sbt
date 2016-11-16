lazy val abideSettings = Seq(
  organization := "com.typesafe",
  version := "0.1-SNAPSHOT"
)

lazy val sharedSettings = Formatting.sbtFilesSettings ++ abideSettings ++ Seq(
  crossScalaVersions := List("2.11.8", "2.12.0"),
  scalaVersion := crossScalaVersions.value.head,
  scalacOptions ++= Seq("-Xfuture", "-deprecation", "-feature" /*, "-Xfatal-warnings"*/),
  testOptions in Test += Tests.Argument("-oF"),
  libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  publishArtifact in Test := false
)

lazy val macros = Project("abide-macros", file("macros")).settings(sharedSettings: _*)

lazy val abide = Project("abide", file("abide"))
  .settings(sharedSettings: _*)
  .settings(
    mappings in(Compile, packageBin) ++= (mappings in(macros, Compile, packageBin)).value,
    mappings in(Compile, packageSrc) ++= (mappings in(macros, Compile, packageSrc)).value
  ).dependsOn(macros % "compile->compile;test->test")

lazy val sbtAbide = Project("sbt-abide", file("sbt-plugin"))
  .settings(sharedSettings: _*)
  .settings(
    sbtPlugin := true,
    crossScalaVersions := Seq("2.10.6"),  
    scalaVersion := crossScalaVersions.value.head
  )

lazy val coreRules = Project("abide-core", file("rules/core"))
  .settings(sharedSettings: _*)
  .dependsOn(abide % "compile->compile;test->test")

lazy val akkaRules = Project("abide-akka", file("rules/akka"))
  .settings(sharedSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.4.12" % "test",
      "com.typesafe.akka" %% "akka-stream" % "2.4.12" % "test"
    )
  )
  .dependsOn(abide % "compile->compile;test->test")

lazy val extraRules = Project("abide-extra", file("rules/extra"))
  .settings(sharedSettings: _*)
  .dependsOn(abide % "compile->compile;test->test")

lazy val rules = Seq(coreRules, akkaRules, extraRules)

lazy val allProjects = Seq(macros, abide, sbtAbide) ++ rules

lazy val filter = ScopeFilter(inAggregates(ThisProject, includeRoot = false))

lazy val root = (Project("root", file(".")).enablePlugins(sbtdoge.CrossPerProjectPlugin)
  .settings(
    //test in Test <<= allProjects.map(p => test in Test in p).dependOn,
    //test in Test      := (test in tests in Test).value,
    //parallelExecution in Global := false,
    packagedArtifacts := Map.empty
  ) /: allProjects)(_ aggregate _)
