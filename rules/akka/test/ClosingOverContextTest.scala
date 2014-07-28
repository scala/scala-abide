package com.typesafe.abide.rules.akka

import scala.tools.abide.test.traversal._
import com.typesafe.abide.akka._

class ClosingOverContextTest extends TraversalTest {

  val rule = new ClosingOverContext(context)

  "Actor `context` value" should "not be closed over" in {
    assert(false, "TODO!!")
  }
}
