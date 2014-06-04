package scala.tools.abide

/**
 * Base trait for state dealt with during rule verification. Rules should output
 * a final state after verification. Since this is the result of a verification,
 * it must provide the warnings discovered during this verification phase.
 */
trait State {
  def warnings : List[Warning]
}
