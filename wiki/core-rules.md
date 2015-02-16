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

## Not checking if Traversables are empty or non empty by comparing their size

name : **empty-nonempty-using-size**
source [EmptyOrNonEmptyUsingSize](/rules/core/src/main/scala/com/typesafe/abide/core/EmptyOrNonEmptyUsingSize.scala)

Comparing size to zero is bad because some subclasses of Traversable has got an expensive size/length calculation.
isEmpty/nonEmpty are always O(1) so use that instead.

## Avoiding by-name right-associative operators

name : **by-name-right-associative**
source : [ByNameRightAssociative](/rules/core/src/main/scala/com/typesafe/abide/core/ByNameRightAssociative.scala)

By-name arguments given to right-associative operators (those ending in `_:`)
will be evaluated before the call to the operator rather than at the argument's use in the operator's body.

### Example

```scala
def foo(): Int = { println("foo"); 1 }

class C {
  def op_:(x: => Int) = println("bar")
}

foo() op_: (new C) // Prints "foo" then "bar"

(new C).op_:(foo()) // Prints just "bar"
```

The user most likely expects that in both cases only "bar" will be printed.

## Avoiding class or object declarations inside package objects

name : **package-object-classes**
source : [PackageObjectClasses](/rules/core/src/main/scala/com/typesafe/abide/core/PackageObjectClasses.scala)

It is not recommended to define classes or objects inside of package objects,
as they do not always work as expected.  See [SI-4344](https://issues.scala-lang.org/browse/SI-4344) for more details.

## Inferences of `Any` or `AnyVal`

name : **infer-any**
source : [InferAny](/rules/core/src/main/scala/com/typesafe/abide/core/InferAny.scala)

The Scala compiler will often infer `Any` as the parameter to a polymorphic
method. This is generally not what is desired and can lead to errors such as
the following:

```scala
1L to 10L contains 3
// => false
```

To avoid the warning in cases where this is intentional, simply specify the
type explicitly:

```scala
1L to 10L contains[Any] 3
1L to 10L contains (3: Any)
```

## Avoiding nullary methods with `Unit` as their return type

name : **nullary-unit**
source : [NullaryUnit](/rules/core/src/main/scala/com/typesafe/abide/core/NullaryUnit.scala)

It is not recommended to define methods with side-effects which take no arguments, as it is easy to accidentally invoke those side-effects.

## Usages of potentially inaccessible types

name : **inaccessible**
source : [Inaccessible](/rules/core/src/main/scala/com/typesafe/abide/core/Inaccessible.scala)

Referencing private types as part of a public interface can lead to methods
being impossible to implement in certain cases. For example in the following
piece of code, YourTrait cannot be extended outside of the `foo` package, even
though it is publicly accessible.

```scala
package foo {
  private[foo] trait PrivateType { }

  trait YourTrait {
    def implementMe(f: Int => (String, PrivateType)): Unit
  }
}
```

## Avoid selection of fields from subclasses of `DelayedInit`

name : **delayed-init-select**
source : [DelayedInitSelect](/rules/core/src/main/scala/com/typesafe/abide/core/DelayedInitSelect.scala)

Selecting a field from a subclass of `DelayedInit` such as `App` will yield
`null` if the object is not initialised, which can be confusing.

## Avoiding overriding non-nullary methods with nullary methods

name : **nullary-override**
source : [NullaryOverride](/rules/core/src/main/scala/com/typesafe/abide/core/NullaryOverride.scala)

Providing a nullary override of a non-nullary method can lead to confusing
errors and should be avoided. Consider for example:

```scala
trait T1 { def m() = 1 }
trait T2 { def m = 2 }

(new T1 {}).m() // => 1
(new T1 with T2 {}).m() // does not compile
```

Mixing in an additional trait causes previously correct code not to compile.

## Avoid shadowing mutable public fields with private fields

name : **private-shadow**
source : [PrivateShadow](/rules/core/src/main/scala/com/typesafe/abide/core/PrivateShadow.scala)

Default constructor arguments create private fields which will shadow parent fields with the same name. When the parent field is mutable this can cause unexpected behaviour:

```scala
class Counter(var x: Int) {
  def increment() = { x = x + 1 }
}
class StringCounter(x: Int) extends Counter(x) {
  override def toString() = x.toString()
}

val c = new StringCounter(0)
c.toString() // => 0
c.increment()
c.toString() // => 0
```

In this example `c.toString()` will always print `0`, even after calling `c.increment`.
