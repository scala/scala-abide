package com.lightbend.abide.akka

import scala.tools.abide.traversal._
import com.lightbend.abide.akka._

class SenderInFutureTest extends TraversalTest {

  val rule = new SenderInFuture(context)

  "Stable sender access" should "be valid out of futures" in {
    val tree = fromString("""
      class Titi extends akka.actor.Actor {
        def receive = {
          case _ => 
            val s = sender()
            s ! "Hello"
        }
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be valid inside of futures" in {
    val tree = fromString("""
      import scala.concurrent._
      import scala.concurrent.ExecutionContext.Implicits.global
      class Titi extends akka.actor.Actor {
        def receive = {
          case _ =>
            val s = sender()
            future {
              s ! "Hello"
            }
        }
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  "Unstable sender access" should "be valid out of futures" in {
    val tree = fromString("""
      class Titi extends akka.actor.Actor {
        def receive = {
          case _ => 
            sender() ! "Hello"
        }
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "be invalid inside of futures" in {
    val tree = fromString("""
      import scala.concurrent._
      import scala.concurrent.ExecutionContext.Implicits.global
      class Titi extends akka.actor.Actor {
        def receive = {
          case _ => 
            future {
              sender() ! "Hello"
            }
        }
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(false) }
  }

}
