# Abide Rules

**Abide** combines flexibility and power by managing simple rules with complex analyzers. Typically, these analyzers will be provided by the framework maintainers but can be contributed by users as well. However, the main focus of user contributions should be on **abide** rules.

## Contributing a rule

Once a new rule has been defined, it must be added to a rule project. These are defined in the `project/Build.scala` sbt build configuration as sub-projects that point to some file path in `scala-abide/rules`. All such rule packages will be separately exported as jars that can be selectively used for project verification. Typically, unless the new rule must logically belong to a new package, it suffices to place the rule inside one of the existing rule `src` folders and it will automatically be built alongside the pre-existing rules.

To specify all rules provided by a rule package, each rule project must contain an xml file `resources/abide-plugin.xml` that specifies which rule classes are provided by that particular package. Build tool integration relies on these xml files to reflectively instantiate rules, so extending these files is mandatory for each new rule. The file structure is as follows:
```xml
<plugin>
  <rule class="com.example.rule.MyRule1" />
  <rule class="com.example.rule.MyRule2" />
  ...
</plugin>
```

Finally, to make sure new extensions are clear/valid and remain so, each rule must feature a short descriptive entry in the corresponding rule project wiki page that clarifies use cases and analysis outputs as well as some unit tests that will help avoid breakage.

Once these steps have been performed, simply submit a pull request to this repository for your rule to be added to the community distribution of **abide**!

## Rule basics

The `Rule` trait therefore only defines a few abstract members:

```scala
val context : Context
```
The `context` value provides shared context between rules. The basic `Context` type simply provides a pointer to `universe : scala.reflect.api.Universe` and provides the compiler cake. The `context` value should be provided by the rule constructor. Actually, since **abide** rules are instantiated reflectively, the _only_ acceptable constructor signature for rules takes a single `val context : Context` as argument.

---

```scala
val analyzer : AnalyzerGenerator
```
The `analyzer` value specifies which sort of analysis should take care of this rule. This value will typically be filled by a specialized rule base-type (like `TraversalRule`) and won't be managed by the rule writer.

---

```scala
type State <: RuleState
type Warning <: RuleWarning
```
The `State` type determines what kind of internal state this rule will be dealing with, and is linked to the `Warning` type through the `RuleState.warnings` method. These type members enforce a certain internal structure and type-safety on rule outputs while remaining quite general to deal with future analyzer definitions.

---

```scala
val name : String
```
Finally, the `name` value provides a human-readable interface to **abide** rules for debugging and other pretty printing related activities.

## Traversal rules

A [traversal rule](/wiki/traversal/traversal-rules.md) is used when verification can be performed by a single unstructured pass through the unit-local (typed) source AST. This is typically the case for context-independent rules where bad patterns can be identified without requiring any knowledge of surrounding code, but more subtle types of properties can also be verified without requiring multiple passes.

Examples of such rules can be found at
- [vars which are never assigned](/rules/core/src/main/scala/com/lightbend/abide/core/ValInsteadOfVar.scala)
- [unused members / arguments / locals](/rules/core/src/main/scala/com/lightbend/abide/core/UnusedMember.scala)
- [renaming parameters with defaults in override](/rules/core/src/main/scala/com/lightbend/abide/core/RenamedDefaultParameter.scala)
- ...

## Moar rulez!

Other types of rules should be coming to **abide** in the future, such as cross-unit and flow-sensitive verification, but no such work exists as of now.
