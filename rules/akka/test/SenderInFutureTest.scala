package com.typesafe.abide.akka.test

import scala.tools.abide.test.traversal._
import com.typesafe.abide.akka._

class SenderInFutureTest extends TraversalTest {
  
  val rule = new SenderInFuture(context)

  "Stable sender access" should "be valid out of futures" in {
    val tree = fromString("""
      import scala.concurrent._
      class Titi extends Actor {
        def receive = {
          case _ => 
            val s = sender()
            s ! "Hello"
        }
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be (true) }
  }

  it should "be valid inside of futures" in {
    val tree = fromString("""
      import scala.concurrent._
      class Titi extends Actor {
        def receive = {
          case _ =>
            val s = sender()
            future {
              s ! "Hello"
            }
        }
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be (true) }
  }

}
