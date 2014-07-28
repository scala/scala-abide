# Abide : Lint tooling for Scala

**Abide** aims to provide users with a simple framework for lint-like rule creation and verification.
**Abide** rules are designed as small, self-contained units of logic which abstract concerns such as traversal and
optimization away from the rule writter.

## Using the tool

**Abide** is only available for sbt (and command line) for now, but will be ported to a Scala-IDE plugin and possibly to maven/gradle/etc as well. To add **abide** verification to an sbt project, three different options are available :

### Sbt Plugin

Activate the sbt-abide plugin in both scala 2.10 and 2.11 projects by extending your `project/plugin.sbt` file with
```scala
addSbtPlugin("com.typesafe" % "abide" % "0.1-SNAPSHOT")
```

Then choose the rule libraries by adding the required jars to your dependencies in your project definitions (eg. `build.sbt`)
```scala
libraryDependencies += "com.typesafe" % "abide-samples" % "0.1-SNAPSHOT" % "abide"
```
Note that one can also use sbt projects as rule libraries by using `dependsOn(rules % "abide")` in the project definition.

This mode can run on scala 2.10 projects by using the compiler `-Xsource:2.10` flag (automatically managed by
the plugin), however one _must_ force the use of the **abide** libraries version built against scala 2.11!

### Compiler Plugin

**Abide** can be activated as a compiler plugin in **scala 2.11** projects by extending the sbt build file with
```scala
libraryDependencies += compilerPlugin("com.typesafe" %% "abide" % "0.1-SNAPSHOT")
scalacOptions ++= Seq(
  "-P:abide:abidecp:<some.rules.classpath>",
  "-P:abide:ruleClass:<some.rule.Class>",
  "-P:abide:analyzerClass:<some.analyzer.generator.Module>",
  ...)
```
or simply add these options to your call to `scalac` to enable **abide** during your standard compilation run.

Since sbt configurations are not available to compiler plugins, the `abidecp` argument is required to specify the
classpath where the **abide** rules are located. In turn, the actual paths to these rules are specified through the
`ruleClass` argument (which will typically appear multiple times, once per rule) and plugin analyzer generators will
appear in `analyzerClass` arguments (also multiple instances).

While slightly more complexe than the following alternative, this mode provides integration capabilities for non-sbt
build tools like eclipse or maven.

### Command line

The **abide** compiler plugin can also be used directly on the command line by adding the plugin to your `scalac` command and using the options described in the [compiler plugin](#compiler-plugin) as cli arguments:
```
scalac -Xplugin:<path/to/abide.jar>                            \
       -P:abide:abidecp:<some.rules.classpath>                 \
       -P:abide:ruleClass:<some.rule.Class>                    \
       -P:abide:analyzerClass:<some.analyzer.generator.Module> \
       ...
```

Note that this feature, as in the compiler plugin case, can only be used on **scala 2.11** projects.

## Existing plugins

The **abide** framework comes with a few pre-made rule packages that can be selectively enabled as discussed in the [previous section](#using-the-tool). The list of available packages along with the associated ivy dependency are:

1. [rules/core](/wiki/core-rules.md) provided by `"com.typesafe" % "abide-core" % "0.1-SNAPSHOT"`

2. [rules/extra](/wiki/extra-rules.md) provided by `"com.typesafe" % "abide-extra" % "0.1-SNAPSHOT"`

3. [rules/akka](/wiki/akka-rules.md) provided by `"com.typesafe" % "abide-akka" % "0.1-SNAPSHOT"`

## Extending Abide

Setting aside bugfixes and basic feature modification, **abide** components are generally loosely coupled and self-contained to enable simple extensions to the framework. Such extensions will typically fall into one of three different categories and will vary in complexity. From simplest (and most useful extension point) to most involved:

1. [rule extension](/wiki/rules.md)  
The **abide** framework initially ships with few rules but provides a powerful extension point to accomodate user-defined
rule verification. These rules can either be defined locally (using the `Project(...).dependsOn(rules % "abide")` construction) or shared over github by submitting new rules as pull requests to this repository.

2. [directive extension](/wiki/extensions.md#adding-new-directives)  
**Abide** rules all share a minimal amount of context, namely the universe instance which defines the compiler AST cake. This context is passed to rules through the `val context : Context` constructor parameter that each rule must define. Directives will typically be mixed in to the shared context object and cache computation results when these can be shared between rules.

3. [analyzer extension](/wiki/extensions.md#defining-analyzers)  
When rule application can benefit from some global traversal logic, or partial tree analysis, these computations are
performed in an `Analyzer` subtype. These analyzers are typically non-trivial and will be integrated into the main **abide** deliverable to then be shared by all rules. However, analyzers can also be provided alongside rules in plugin
libraries.

The provided extension mechanism uses a plugin architecture based on an xml description that specifies plugin
capabilities. This description sits at the base of the `resources` directory, in `abide-plugin.xml` and has the 
following structure:
```xml
<plugin>
  <rule class="some.rule.Class" />
  <rule class="some.other.rule.Class" />
  <analyzer class="some.analyzer.generator.Class" />
</plugin>
```

Directives don't need to (and shouldn't) be specified in the plugin description as they will be statically referenced
in the source code (and they can't be dynamically subsumed like analyzers).

## Further Work

- The **abide** compiler plugin needs to manage rule enabling and disabling. This should be implemented in
`scala.tools.abide.compiler.AbidePlugin` in the `component.apply` method by replacing the analyzer filter (first argument of
```scala gen(_ => true)(unit.body)```) by an actual filter.

- Extending build tool support. **Abide** plugins for eclipse, gradle, maven, etc would be a nice feature extension to the
framework. Such extensions should be relatively easy to implement by using the compiler plugin and manually injecting
`-P:abide:...` command line arguments to configure the framework. **Abide** could also be run on the result of a presentation
compiler run, and `scala.tools.abide.compiler.AbidePlugin` would be a good place to start for loading rules, contexts,
generators, etc. One can also look at the tests to witness **abide** verification in action without the compiler plugin.

- Adding new `Presenter` types. For now, **abide** can only output warnigns as compiler warnings, but it would be nice, for
example, to be able to output interactive HTML5 reports.

- Enabling cross-unit rules by writing a new analyzer. The base would resemble that of the `FusingTraversalAnalyzer` but
would additionnaly require keeping track of the result of previous traversals for incremental compilation integration.
