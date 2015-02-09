# Writing Scoping Rules

Rules that use scala-style scoping information to lookup symbols given names, for example, can use the `ScopingRule` base trait to do so. As in the [warning rules](/wiki/traversal/warning-rules.md) case, scoping rules simply accumulate warnings with a `nok` helper method based on local context and scoping information, which is automatically tracked by the `ScopingRule` trait behind the scenes. A helper method is provided by the traversal state to lookup symbols given a name and a filter during traversal
```scala
def lookup(name: Name, qualifies: Symbol => Boolean): NameLookup
```
and can be accessed on the state value as `state.lookup(name, filter)`.

The scope tracking implementation is based on that of the scala compiler and aims to provide scoping information that is as faithful as possible.
