# Writing Existential Rules

We will illustrate here the `ExistentialRule` base-trait that extends `TraversalRule`. Existential rules generate warnings by traversing the whole tree and validating / invalidating shared _keys_ that are extracted from tree nodes. An invalid key will remain so until it is validated, and validated keys cannot be invalidated. This structure is necessary since we can't guarantee key visiting order, so we must make sure there is a precedence relation. Once a full traversal has been performed, warnings are generated based on the keys that remain invalid.

The `ExistentialRule` trait therefore defines a `State` type that encodes the behaviour we just described. Furthermore, it provides two helper methods:
```scala
def ok(key : Key) : Unit
def nok(key : Key, warning : Warning) : Unit
```
that will validate (`ok`), respectively invalidate (`nok`), these keys. The `Key` type remains an abstract member of the `ExistentialRule` type since different types of keys can be used in different concrete rules. Note that `nok` also takes a `Warning` argument that specifies the concrete warning that will be output by the rule if the associated key remains invalid after a full AST traversal.

To showcase an `ExistentialRule` usage example, we will present a rule that checks that every private `var` member of a class is assigned somewhere in the class body (or it should be a `val`). Since we can't assume anything about declaration / assignment ordering, we use `ExistentialRule` as a base-trait and validate assignments while invalidating variable declarations. You can see the full source at [ValInsteadOfVar](/rules/core/src/main/scala/com/typesafe/abide/core/ValInsteadOfVar.scala).

Let us start by defining the rule class:
```scala
import scala.tools.abide._
import scala.tools.abide.traversal._

class MemberValInsteadOfVar(val context : Context) extends ExistentialRule {
  val name : String = "member-val-instead-of-var"
}
```

The next step is to define the warning type. This is done by providing a `Warning` case class that provides a meaningful warning message:
```scala
import context.universe._

case class Warning(tree : Tree) extends RuleWarning {
  val pos = tree.pos
  val message = s"The member `var` $tree was never assigned " +
                 "locally and should therefore be declared as a `val`"
}
```

Finally, once the groundwork has been laid, we need to perform the actual verification. As mentioned previously, we use the `ok` and `nok` helper methods to validate / invalidate the `var` symbols when these are encountered in the tree walk. Since we are only interested by declarations and assignments, we only need to match those trees in the `step` partial function.
```scala
val step = optimize {
  case varDef @ q"$mods var $name : $tpt = $value" =>
    val setter : Symbol = varDef.symbol.setter
    // only invalidate private variables
    if (setter.isPrivate) nok(varDef.symbol, Warning(varDef))

  // assignments are handled internally through setters,
  // so that's what we search for!
  case set @ q"$setter(..$args)" if setter.symbol.isSetter =>
    // the variable is valid once assigned
    ok(setter.symbol.accessed)
}
```

And... we're done!
