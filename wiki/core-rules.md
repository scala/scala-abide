# Core rules package [rules/core](/rules/core/src)

Style rules that apply to all (or most) Scala code and are (mostly) non-controversial should be placed in this rule package, which should be included in most **abide** verifications.

## Unused local definitions

name : **unused-member**  
source : [UnusedMember](/rules/core/src/main/scala/com/typesafe/abide/core/UnusedMember.scala)

Definitions that cannot be accessed outside of the current scope should _always_ be used in the current scope. If this is not the case, then these definitions shouldn't exist as they reduce code readability by bloating the current scope.

This rule applies to:
- private class members
- non-overriding method arguments
- block-local values and definitions

## Public mutable fields

name : **public-mutable-fields**  
source : [PublicMutable](/rules/core/src/main/scala/com/typesafe/abide/core/PublicMutable.scala)

Class and objects shouldn't expose mutable values (or variables) through their public API, such members should be hidden behind a transformation API to ensure good behaviour.

Are considered invalid mutable fields:
- visible `var` definitions
- visible `val` definitions that extend `Mutable`
- visible `val` definitions whose type has transitively accessible mutable members

## Renamed default parameters in override

name : **renamed-default-parameter**  
source : [RenamedDefaultParameter](/rules/core/src/main/scala/com/typesafe/abide/core/RenamedDefaultParameter.scala)

Method arguments that have default values should _never_ have their names swapped when overriding the method as this can have extremely unexpected behaviour (see [ScalaPuzzlers](http://scalapuzzlers.com/#pzzlr-024)).

## Unexpected recursive definitions

name : **stupid-recursion**  
source : [StupidRecursion](/rules/core/src/main/scala/com/typesafe/abide/core/StupidRecursion.scala)

Parameterless methods shouldn't be recursive as such code is unclear and requires atypical termination conditions. Typically, one would not write such code, but a common programming pattern is member delegation on trait (or abstract class) instantiation. If one doesn't prefix the delegated member with the correct scoping information, we get a recursive definition (which can be valid, yet unwanted, Scala code).

For example, in the following trait delegation example
```scala
trait Container {
  def list : List[Int]
  def with(i : Int) : Container = new Container {
    def list : List[Int] = list
  }
}
```
the `Container.with` method is valid Scala code but will generate a never-ending loop at runtime.

## Variables that are never assigned

name : **local-val-instead-of-var** and **member-val-instead-of-var**  
source : [ValInsteadOfVar](/rules/core/src/main/scala/com/typesafe/abide/core/ValInsteadOfVar.scala)

Private (or local) `var` definitions that have no assignment should actually be defined as `val` to clarify intent.

## Matching `Seq`-typed value with `::`

name : **match-case-on-seq**  
source : [MatchCaseOnSeq](/rules/core/src/main/scala/com/typesafe/abide/core/MatchCaseOnSeq.scala)

When performing case matching, `Seq` typed scrutinees should not be deconstructed by `::` case statements since this reduces typing clarity. For example, the following snippet
```scala
(l : Seq[Int]) match {
  case x :: xs => x
  case Nil => 0
}
```
should be written as
```scala
(l : Seq[Int]) match {
  case seq if seq.nonEmpty => seq.head
  case Nil => 0
}
```

## Passing a sequence to a repeated parameter

name : **seq-in-repeated-parameter-position**  
source : [SeqToRepeatedParameter](/rules/core/src/SeqToRepeatedParameter.scala)

Most collections have an `apply` constructor with repeated parameters, allowing to enumerate the elements of the collection. When the user passes only one argument of a sequence type the result can be confusing. Instead of copying the given sequence, another collection of one element is return.

### Example
```scala
val buf = ListBuffer(1, 2)
Vector(buf) // Vector[ListBuffer[Int]], instead of Vector[Int]
```

Most likely the user would like this instead:
```scala
val buf = ListBuffer(1, 2)
Vector(buf: _*) // a Vector[Int](1, 2)
```

This rule warns of all cases where a sequence type is passed instead of a repeated parameter argument, when the repeated parameter type is a type variable.