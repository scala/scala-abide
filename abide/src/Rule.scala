package scala.tools.abide

/**
 * Base class for all verification rules the framework will deal with
 */
trait Rule {
  val analyzer : Analyzer
  
  /** We require a name field to manage rules (enable/disable) */
  val name : String
}
