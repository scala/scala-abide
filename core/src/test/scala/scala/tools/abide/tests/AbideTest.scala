package scala.tools.abide
package tests

import scala.tools.nsc.interactive._
import scala.tools.nsc.reporters._
import org.scalatest._

abstract class AbideTest extends FlatSpec with Matchers with CompilerProvider with TreeProvider
