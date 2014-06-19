package scala.tools.abide

import scala.reflect.api._

/**
 * Base trait for warnings discovered by the verification framework.
 * Each warning has a position and a rule from which it comes. This rule
 * can then be used to add extra capabilities to the warning (like
 * quickfix).
 */
trait Warning {
  val pos  : Position
  val rule : Rule
}
