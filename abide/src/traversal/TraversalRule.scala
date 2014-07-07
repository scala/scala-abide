package scala.tools.abide.traversal

import scala.tools.abide._
import scala.reflect.internal.traversal._

/**
 * TraversalRule
 *
 * Base-class for rules based on single traversal of source tree. Traversal is powered by
 * classes in the scala.reflect.internal.traversal package and rules can therefore be fused
 * to increase traversal speed.
 *
 * Traversal is implemented by defining the `step` value of a rule, which is a partial
 * function from universe.Tree to Unit. Each step should update the internal state of the
 * traversal by using the `transform` method from the `Traversal` class. Typically, one will
 * provide a few helper methods in intermediate [[TraversalRule]] subclasses to make rule
 * definitions clearer (see [[ExistentialRule]] for example). State will only be consumed once
 * the traversal has terminated, so although partial states can allways be accessed, these aren't
 * necessarily meaningful (eg. in [[ExistentialRule]], issues can be invalidated after they were
 * discovered and warnings can therefore only be collected after a full traversal).
 *
 * Since we are extending the OptimizingTraversal trait, we can use the `optimize` macro on our
 * step function to enable traversal fusing. A step value will typically look something like :
 *
 *   val step = optimize {
 *     case vd : ValDef => // transform state somehow
 *     case dd : DefDef => // transform state some-other-how
 *   }
 *
 * If scoping information is needed for the rule, the ScopingTraversal trait can be mixed in
 * to the rule to provide the necessary helpers. Fusing and application will be seamlessly
 * handled by the encapsulating framework. Note that the [[NaiveTraversalAnalyzerGenerator]]
 * shipped in [[TraversalRule]] by default will be replaced by a [[FusingTraversalAnalyzerGenerator]]
 * by the framework since it subsumes the naive version (see [[scala.tools.abide.compiler.AbidePlugin]] for more info).
 */
trait TraversalRule extends OptimizingTraversal with Rule {
  val universe : context.universe.type = context.universe

  val analyzer = NaiveTraversalAnalyzerGenerator
}
