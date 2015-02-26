# Writing Path Rules

Rules that need to track hierarchical traversal path information can take advantage of the `PathRule` base trait to do so. Much like for [warning rules](/wiki/traversal/warning-rules.md), path rules simply accumulate warnings by using a `nok` helper method. However, they also provide an entry helper that enables simple traversal path accumulation:
```scala
def enter(elem : Element) : Unit
```
will add `elem` to the path stack and the `state` provider can be queried to check whether the path currently matches a certain sequence of elements:
```scala
def matches(elems : Element*) : Boolean
```

We also provide a `last` method that gives access to the current containing element.

To illustrate `PathRule` usage, we will implement a recursive method definition checker that searches for weird definitions that point to themselves (see the full source at [StupidRecursion](/rules/core/src/main/scala/com/typesafe/abide/core/StupidRecursion.scala)).

We start by defining the rule class:
```scala
import scala.tools.abide._
import scala.tools.abide.traversal._

class StupidRecursion(val context : Context) extends PathRule {
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

To manage the traversal path, we want to register entry of any method definition in the `step` partial function. To implement this, we use the `enter` helper and apply it to any member `def` declaration discovered during AST traversal:
```scala
case defDef @ q"def $name : $tpt = $body" => enter(defDef.symbol)
```

Traversal path is automatically handled by the `PathRule` trait, and we can now query the internal traversal state with `state.matches(sym)` to evaluate the current path state.

Now that we have traversal ordering, we implement stupid recursion checking by simply verifying that definition access doesn't point to the current scope (`state matches tree.symbol`):
```scala
case id @ Ident(_) if id.symbol != null && (state matches id.symbol) =>
  nok(Warning(id))
case s @ Select(_, _) if s.symbol != null && (state matches s.symbol) =>
  nok(Warning(s))
```

And... we're done!
