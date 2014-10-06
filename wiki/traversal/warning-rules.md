# Writing Warning Rules

Let us illustrate the `WarningRule` base-trait by writing a rule that makes sure overrides don't change argument names (see [FixedNameOverrides](/rules/extra/src/main/scala/com/typesafe/abide/extra/FixedNameOverrides.scala) for the full source). Since the typing phase will provide us with symbols for all definitions that a particular member overrides, we only need local information to verify whether a definition is valid or not, and we can verify the rule in a single pass through the program AST. Therefore, the `WarningRule` trait is exactly what we need!

The `WarningRule` trait defines a `State` type that simply accumulates warnings. These warnings are added to the state by invoking the `nok(warning : Warning) : Unit` helper provided by the `WarningRule trait. This is pretty much the simplest way possible of generating warnings during a traversal.

First off, let's start by writing a new class that extends the `WarningRule` trait.
```scala
import scala.tools.abide._             // brings Context into scope
import scala.tools.abide.traversal._   // brings the WarningRule trait into scope
 
class FixedNameOverrides(val context : Context) extends WarningRule {
  val name = "fixed-name-overrides"
}
```

Once we have the base class, we can define the shape of the warnings this rule will report by defining the type member `Warning` of the `Rule` trait.
```scala
import context.universe._              // brings scala syntax trees into scope

case class Warning(vd : ValDef, sn : String, sym : MethodSymbol) extends RuleWarning {
  val pos = vd.pos
  val message = s"Renaming parameter ${vd.name} of method ${vd.symbol.owner.name}" +
                s"from $sn in super-type ${sym.owner.name} can lead to confusion"
}
```
To make sure we have a meaningful warning message, we define the `Warning` type with all the necessary parameters. We see here why having users provide their own `Warning` type helps making reporting clear and precise.

The only remaining member that needs a definition is the `step` value which will actually describe warning accumulation. Since we're dealing with method overrides checking, we only need to consider `DefDef` trees during traversal, and we use the `optimize` macro to benefit from a fusion speedup:
```scala
val step = optimize {
  case defDef : DefDef =>
    check(defDef)
}
```
where the `check` function can be implemented as
```scala
def check(defDef : DefDef) {
  if (!defDef.symbol.isSynthetic && !defDef.symbol.owner.isSynthetic) {
    // iterate over all definitions that defDef overrides
    defDef.symbol.overrides.foreach { overriden =>

      // iterate over all parameter pairs (defDef.param, overriden.param) which are in the same position
      (defDef.vparamss.flatten zip overriden.asMethod.paramLists.flatten).foreach { case (vd, o) =>

        // if the name changed, then we emit a warning
        if (vd.symbol.name != o.name) {
          // we use the `nok` helper method we described previously
          nok(Warning(vd, o.name.toString, overriden.asMethod))
        }
      }
    }
  }
}
```

And... we're done! Now don't forget to add the rule to the `resources/abide-plugin.xml` file for the sbt **abide** plugin to automatically detect this rule when verifying your scala projects.

