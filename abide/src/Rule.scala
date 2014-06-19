package scala.tools.abide

/**
 * Base class for all verification rules the framework will deal with
 */
trait Rule {
  val analyzer : Analyzer

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
