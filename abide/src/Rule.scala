package scala.tools.abide

import scala.tools.nsc._

object Rule extends ContextGenerator {
  def mkContext(global : Global) : Context = new Context(global)
}

/**
 * Base class for all verification rules the framework will deal with
 */
trait Rule {
  val context : Context
  val analyzer : AnalyzerGenerator

  trait RuleState {
    def warnings : List[Warning]
  }

  type State <: RuleState

  trait RuleWarning extends scala.tools.abide.Warning {
    val rule : Rule = Rule.this
  }

  type Warning <: RuleWarning

  /** We require a name field to manage rules (enable/disable) */
  val name : String
//  val description : String
}
