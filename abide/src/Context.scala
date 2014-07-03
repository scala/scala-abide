package scala.tools.abide

import scala.tools.nsc._

 /**
 * Base trait for context generator objects that provide rules with shared context.
 * 
 * If some information requires heavy computing and can be shared between two or more rules, it may be
 * useful to cache the result in a common place. To enable this, build a new type that extends the [[Context]]
 * type with these capabilities and provide these rules with a companion object that extends [[ContextGenerator]].
 * The abide framework will automatically load these objects and compute the lub of shared information for each
 * rule and instantiate the contexts accordingly.
 *
 * @see [[Context]]
 * @see com.typesafe.abide.sample.PublicMutable for a concrete example
 */
trait ContextGenerator {
  def mkContext(global : Global) : Context
}

/**
 * Context base-class that lets rules share common information (like the compiler instance [[global]]).
 * 
 * More information can be added to the shared context through an extension and companion class mechanism described
 * in more detail in [[ContextGenerator]].
 */
class Context(val global : Global)

