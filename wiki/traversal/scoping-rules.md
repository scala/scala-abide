# Writing Scoping Rules

Rules that need to maintain scala-style scopes for symbols can use the `ScopingRule` trait to construct and
access such scopes during verification. Such rules accumulate warnings by using the `nok(warning: Warning)`
helper method and construct a scala symbol scope thanks to
```scala
def enterScope(): Unit
```
which will register a new scoping environment to the scoping stack, and
```scala
def scope(elem: Element): Unit
```
which adds `elem` to the current scope (the one at the top of the scoping stack). When the tree for which
`enterScope()` was called is left by traversal, the current scoping environment is popped from the stack, thus
enforcing a scala-equivalent scoping style.

To access scope, we provide the helper function
```scala
def lookup(matcher: Element => Boolean): Option[Element]
```
that will traverse the scoping stack top-down and return the first element for which `matcher` is satisfied.

In order to write a Scoping Rule, one must start by defining the rule class:
```scala
import scala.tools.abide._
import scala.tools.abie.traversal._

class MyScopingRule(val context: Context) extends ScopingRule {
  val name = "my-scoping-rule"
}
```
and we provide the required `Warning` class:
```scala
case class Warning(tree: Tree) extends RuleWarning {
  val pos = tree.pos
  val message = "Houston, we have a problem in $tree"
}
```

We still need to define the `Element` type. Assuming we are interested in the scoping of value definitions,
we want to add `ValDef` symbols to the scope and therefore
```scala
type Element = Symbol
```

Finally, to manage scoping, we must register new scopes when these would appear in scala. Typically, this occurs on
`Block` entry. This leads to the following step function:
```scala
val step = optimize {
  case b: Block => enterScope()
  case v: ValDef => scope(v.symbol)
  // case ... => actually do some checking!!
}
```

And we have scala-style value definition scoping. Symbols can now be looked up by name, for example, by calling
`state.lookup(_.name == "foo")` and masking will be automatically managed by the `ScopingRule` state.
