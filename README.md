# Abide : Lint tooling for Scala

Abide aims to provide users with a simple framework for lint-like rule creation and verification.
Abide rules are designed as small, self-contained units of logic which abstract concerns such as traversal and optimization away from the rule writter.

As of now, only the backend for unit-local, flow-agnostic rules has been written, but cross-unit and flow-sensitive backends should be added in the
future (if deemed useful).

## Using the tool

Abide is only available for sbt for now, but will be ported to a Scala-IDE plugin and possibly to maven/gradle/etc as well. To add Abide verification to an
sbt project, two different options are available :

1. add it as a compiler plugin (only available for scala 2.11 projects)

Extend the sbt file with
```scala
libraryDependencies += compilerPlugin("com.typesafe" %% "abide" % "0.1-SNAPSHOT")
scalacOptions ++= Seq("-P:abide:abidecp:<some.rules.path>+", "-P:abide:ruleClass:<some.rule.Class>", ...)
```
or simply add these options to your call to `scalac` to enable Abide during your standard compilation run.

Since sbt configurations are not available to compiler plugins, both the classpath to the jar files containing Abide rules _and_ the complete class name of
the rules we wish to activate are necessary for the plugin to work. This mode does however provide simple integration for non-sbt build tools like eclipse or maven.

2. use the sbt-abide plugin by adding ```scala libraryDependencies += "com.typesafe" % "abide_2.11" % "0.1-SNAPSHOT"``` to your `project/plugins.sbt` file
and then choose the rule libraries by adding the required jars to your dependencies with 
```scala libraryDependencies += "com.typesafe" % "abide-samples_2.11" % "0.1-SNAPSHOT"```. Note that this mode can run on scala 2.10 projects by using the compiler
`-Xsource:2.10` flag, however the libraries _must_ use the scala 2.11 version!
```scala

## Extending Abide

Many simple(r) rules do not actually need flow information and warnings can be collected by a single pass through a compilation unit's body. Such rules are
called _traversal rules_ in the Abide lingo. See ...

##
