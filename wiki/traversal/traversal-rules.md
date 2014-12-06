# Writing Traversal Rules

Many lint rules can be verified by a single pass through the program source code. To make writing such rules easy, **abide** provides the `TraversalRule` trait as extension point.

Traversal rules collect warnings by maintaining internal state and visiting each tree node once. Default traversal has no guarantees on traversal ordering and rules must therefore be completely agnostic. However, one can request hierarchical traversal ordering (parents visited before children) by mixing the `ScopingTraversal` trait into the rule definition.

## Existing helpers

In addition to being verifiable with a single pass, rules will typically share some common behaviour even within the `TraversalRule` context, and three sub-trait extensions are defined by the **abide** framework:

1. [warning rules](/wiki/traversal/warning-rules.md) provide helper methods by extending the `WarningRule` trait to collect local warnings that don't depend on any context / external information. Such rules will typically rely on typer information to determine validity of a tree.

2. [existential rules](/wiki/traversal/existential-rules.md) rely on more than local context and will need to collect information from different parts of a tree to verify a property. A recurring pattern uses validation / invalidation of certain _keys_ depending on local context and is provided by the `ExistentialRule` trait. In an `ExistentialRule`, once a key has been marked as valid, it will remain so for ever, whereas invalid keys can be validated (and will then remain valid). After a full pass through an AST, keys that remain invalid will generate warnings.

3. [path rules](/wiki/traversal/path-rules.md) rely on the hierarchical ordering of the traversal (by mixing `ScopingTraversal` in) to verify program properties. These rules can access state to verify whether a sequence of is-in relations are satisfied when visiting a given node and can use this information to generate warnings. This will typically be useful when verifying recursion or inner / outer relations. The `PathRule` trait provides helpers to manage and access the current "path" traversal has taken.

4. [scoping rules](/wiki/traversal/scoping-rules.md) provides helper methods to maintain a scala-style scope on symbols. Such rules also rely on the `ScopingTraversal` for hierarchical information and provide support for creating new scopes and registering symbols to these. This base-type is especially useful when dealing with masking or symbol lookup based on their name.

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
