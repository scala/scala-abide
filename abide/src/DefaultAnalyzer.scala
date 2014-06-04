package scala.tools.abide

import scala.tools.nsc._

import directives._
import traversal._

class DefaultAnalyzer(val global : Global) extends Analyzer with TraversalAnalyzer with MutabilityChecker {
  import global._

  val components = Seq(
    new TraversalComponent(this)
  )

  val rules = Seq(
    new LocalValInsteadOfVar  (this),
    new MemberValInsteadOfVar (this),
    new StupidRecursion       (this),
    new MatchCaseOnSeq        (this),
    new PublicMutable         (this)
  )
}
