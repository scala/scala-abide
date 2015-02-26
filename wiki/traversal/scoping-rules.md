# Writing Scoping Rules

Rules that use scala-style scoping information to lookup symbols given names, for example, can use the `ScopingRule` base trait to do so. As in the [warning rules](/wiki/traversal/warning-rules.md) case, scoping rules simply accumulate warnings with a `nok` helper method based on local context and scoping information, which is automatically tracked by the `ScopingRule` trait behind the scenes. A helper method is provided by the `ScopeProvider` context mixin to access `ScopingContext` instances for each tree. The `ScopingContext` enables name-based symbol lookup through
```scala
def lookupSymbol(name: Name, suchThat: Symbol => Boolean): NameLookup
```
as well as an `owner: Symbol` member that gives access to the symbol containing the current context. Finally, one can also access the enclosing context through `outer: ScopingContext`.

The scope tracking implementation is based on that of the scala compiler and aims to provide scoping information that is as faithful as possible. However, due to the position-based lookup the system uses, one cannot access the context of trees that don't have an associated position. This is notably the case for `EmptyTree`, `noSelfType`, `pendingSuperCalls` and `TypeTree() if tree.tpe == NoType`. Such considerations only have a limited impact on rule creation as most of these trees are compiler artifacts.
