# Abide : Lint tooling for Scala

Abide aims to provide users with a simple framework for lint-like rule creation and verification.
Abide rules are designed as small, self-contained units of logic which abstract concerns such as traversal and
optimization away from the rule writter.

## Using the tool

Abide is only available for sbt for now, but will be ported to a Scala-IDE plugin and possibly to maven/gradle/etc as
well. To add Abide verification to an sbt project, two different options are available :

### Compiler Plugin

Abide can be activated **in scala 2.11** projects by extending the sbt build file with
```scala
libraryDependencies += compilerPlugin("com.typesafe" %% "abide" % "0.1-SNAPSHOT")
scalacOptions ++= Seq(
  "-P:abide:abidecp:<some.rules.path>+",
  "-P:abide:ruleClass:<some.rule.Class>",
  "-P:abide:analyzerClass:<some.analyzer.generator.Module>",
  ...)
```
or simply add these options to your call to `scalac` to enable Abide during your standard compilation run.

Since sbt configurations are not available to compiler plugins, the `abidecp` argument is required to specify the
classpath where the Abide rules are located. In turn, the actual paths to these rules are specified through the
`ruleClass` argument (which will typically appear multiple times, once per rule) and plugin analyzer generators will
appear in `analyzerClass` arguments (also multiple instances).

While slightly more complexe than the following alternative, this mode provides integration capabilities for non-sbt
build tools like eclipse or maven.

### Sbt Plugin

Activate the sbt-abide plugin in both scala 2.10 and 2.11 projects by adding
```scala
libraryDependencies += "com.typesafe" % "abide" % "0.1-SNAPSHOT"
```
to your `project/plugins.sbt` file and then choose the rule libraries by adding the required jars to your library 
dependencies with 
```scala
libraryDependencies += "com.typesafe" % "abide-samples" % "0.1-SNAPSHOT" % "abide"
```

Note that this mode can run on scala 2.10 projects by using the compiler `-Xsource:2.10` flag (automatically managed by the plugin), however one _must_ force the use of the Abide libraries version built against scala 2.11!

## Extending Abide

As of now, only the backend for unit-local, flow-agnostic rules has been written, but cross-unit and flow-sensitive
backends should be added in the future (if deemed useful). However, many simple(r) rules do not actually need flow
information and warnings can be collected by a single pass through a compilation unit's body. Such rules are called
_traversal rules_ in the Abide lingo. See [writing traversal rules](https://github.com/samarion/scala-abide/wiki/Writing-Traversal-Rules) and [abide plugins](https://github.com/samarion/scala-abide/wiki/Abide-Plugins) for more details.

