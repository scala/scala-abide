# Writing Traversal Rules

Many lint rules can be verified by a single pass through the program source code. To make writing such rules easy, **abide** provides the `TraversalRule` trait as extension point.

Traversal rules collect warnings by maintaining internal state and visiting each tree node once. Default traversal has no guarantees on traversal ordering and rules must therefore be completely agnostic. However, one can request hierarchical traversal ordering (parents visited before children) by mixing the `ScopingTraversal` trait into the rule definition.

## Existing helpers

In addition to being verifiable with a single pass, rules will typically share some common behaviour even within the `TraversalRule` context, and three sub-trait extensions are defined by the **abide** framework:

1. [warning rules](/wiki/traversal/warning-rules.md) provide helper methods by extending the `WarningRule` trait to collect local warnings that don't depend on any context / external information. Such rules will typically rely on typer information to determine validity of a tree.

2. [existential rules](/wiki/traversal/existential-rules.md) rely on more than local context and will need to collect information from different parts of a tree to verify a property. A recurring pattern uses validation / invalidation of certain _keys_ depending on local context and is provided by the `ExistentialRule` trait. In an `ExistentialRule`, once a key has been marked as valid, it will remain so for ever, whereas invalid keys can be validated (and will then remain valid). After a full pass through an AST, keys that remain invalid will generate warnings.

3. [scoping rules](/wiki/traversal/scoping-rules.md) rely on scoping information (by mixing `ScopingTraversal` in) to verify program properties, a situation that will generally arise when verifying properties dealing with recursion or inner / outer relations. The `ScopingRule` interfaces provides helpers to manage and access scope during AST traversal to verify such properties.

Also, many rules share the same basic warning structure of a message linked to a particular tree. In that case, one can mix the `SimpleWarnings` trait into the rule definition and simplify warning definition to filling out the `warning` value member of the rule with a function from `Tree` to `String`. Furthermore, a string interpolator `w` is provided as well that enables reference to the tree we are warning against in the message as well as even simpler definitions:
```scala
val warning = w"The code in $tree is bad!"
```

## Adding new helper traits

Many traversal rules can be implemented by using the previous helper base-traits, but some may require a slightly different state representation, or different helpers. Here are a few considerations to keep in mind when writing new traversal base-traits.

### Initial state

The internal state maintained by traversal rules is defined by two abstract members of `TraversalRule`, namely 
```scala
type State <: RuleState
def emptyState : State
```
where `State` defines the concrete internal state type and `emptyState` provides initial state before a traversal begins. Actual initialization is automatically and internally managed by the traversal analyzer.

The `State` type must simply define the `def warnings : List[Warning]` method that converts the current state to a list of framework warnings that will be consumed by the framework once traversal is finished.

### State transformation

To define the actual state transformation applied at each tree node, one must define the
```scala
val step : PartialFunction[Tree, Unit]
```
member of the `TraversalRule` subtype. This partial function relies on
```scala
def transform(f : State => State) : Unit
```
that updates the internal state of the traversal rule. If `ScopingTraversal` has been mixed in, we also gain access to
```scala
def transform(enter : State => State, leave : State => State) : Unit
```
where the `leave` function is applied to the state when the traversal leaves a node. In practice, the `transform` functions are typically not called in user-defined rules but are abstracted away by helper methods such as `WarningRule.nok` or `ScopingRule.enter`.

To enable traversal rule fusing (which can drastically increase performance), one can use the `optimize` macro surrounding the `step` partial function:
```scala
val step = optimize {
  case vd : ValDef =>
  case dd : DefDef =>
}
```
