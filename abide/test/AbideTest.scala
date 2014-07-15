package scala.tools.abide.test

import scala.reflect.internal.traversal.test._
import org.scalatest._

abstract class AbideTest extends FlatSpec with Matchers with TreeProvider
