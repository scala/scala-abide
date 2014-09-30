package com.typesafe.abide.akka

import scala.tools.abide.traversal._
import com.typesafe.abide.akka._

class ClosingOverContextTest extends TraversalTest {

  val rule = new ClosingOverContext(context)

  "Actor `context` value" should "be valid out of closure" in {
    val tree = fromString("""
      import akka.stream._
      import akka.stream.scaladsl._
      trait Titi extends akka.actor.Actor {
        private val materializer = FlowMaterializer(MaterializerSettings())
        def receive = {
          case "become" => context become {
            case _ =>
          }
          case _ => Flow(() => 0).onComplete(materializer) {
            case _ => self ! "become"
          }
        }
      }
    """)

    global.ask { () => apply(rule)(tree).isEmpty should be(true) }
  }

  it should "not be closed over in onComplete" in {
    val tree = fromString("""
      import akka.stream._
      import akka.stream.scaladsl._
      trait Titi extends akka.actor.Actor {
        private val materializer = FlowMaterializer(MaterializerSettings())
        def receive = {
          case _ => Flow(() => 0).onComplete(materializer) {
            case _ => context become {
              case _ =>
            }
          }
        }
      }
    """)

    global.ask { () => apply(rule)(tree).size should be(1) }
  }
}
