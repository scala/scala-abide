# Writing Scoping Rules

Rules that need to track scoping information can take advantage of the `ScopingRule` base trait to do so. Much like for [warning rules](/wiki/traversal/warning-rules.md), scoping rules simply accumulate warnings by using a `nok` helper method. However, they also provide a scoping helper that enables simple scope accumulation:
```scala
def enter(owner : Owner) : Unit
```
will add `owner` to the scoping stack and the `state` provider can be queried to check whether we are currently in a particular owner with another helper:
```scala
def in(owner : Onwer) : Boolean
```

To illustrate `ScopingRule` usage, we will implement a recursive method definition checker that searches for weird definitions that point to themselves (see the full source at [StupidRecursion](/rules/core/src/main/scala/com/typesafe/abide/core/StupidRecursion.scala)).

We start by defining the rule class:
```scala
import scala.tools.abide._
import scala.tools.abide.traversal._

class StupidRecursion(val context : Context) extends ScopingRule {
  val name = "stupid-recursion"
}
```
and we provide the required `Warning` type:
```scala
case class Warning(tree : Tree) extends RuleWarning {
  val pos = tree.pos
  val message = s"The value $tree is recursively used " +
                 "in it's directly defining scope"
}
```

To manage scoping, we want to register entry of any method definition in the `step` partial function. To implement this, we use the `enter` helper and apply it to any member `def` declaration discovered during AST traversal:
```scala
case defDef @ q"def $name : $tpt = $body" => enter(defDef.symbol)
```

Scope is automatically handled by the `ScopingRule` trait, and we can now query the internal traversal state with `state.in(sym)` to evaluate the current scoping state.

Now that we have scope, we implement stupid recursion checking by simply verifying that definition access doesn't point to the current scope (`state in tree.symbol`):
```scala
case id @ Ident(_) if id.symbol != null && (state in id.symbol) =>
  nok(Warning(id))
case s @ Select(_, _) if s.symbol != null && (state in s.symbol) =>
  nok(Warning(s))
```

And... we're done!
