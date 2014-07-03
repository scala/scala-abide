package scala.tools.abide.test

import scala.tools.abide._

import scala.tools.nsc.interactive._
import scala.tools.nsc.reporters._
import org.scalatest._

abstract class AbideTest extends FlatSpec with Matchers with CompilerProvider with TreeProvider
