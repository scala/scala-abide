package com.typesafe.abide.akka

import scala.tools.abide._
import scala.tools.abide.traversal._

class SenderInFuture(val context : Context) extends ScopingRule {
  import context.universe._

  val name = "sender-in-future"

  case class Warning(tree : Tree) extends RuleWarning {
    val pos = tree.pos
    val message = s"sender() is not stable and should not be accessed in non-thread-safe environments"
  }

  sealed abstract class Owner
  case object Actor   extends Owner
  case object Receive extends Owner
  case object Future  extends Owner

  lazy val actorSym = rootMirror.getClassByName(TypeName("akka.actor.Actor"))
  lazy val senderSym = actorSym.members.find(_.name == TermName("sender")).get
  lazy val futureSym = rootMirror.getModuleByName(TermName("scala.concurrent")).members.find(_.name == TermName("future")).get

  val step = optimize {
    case classDef : ClassDef if classDef.symbol.asClass.baseClasses.exists(_ == actorSym) =>
      enter(Actor)
    case q"def receive : $tpt = $rhs" if state in Actor =>
      enter(Receive)
    case q"$caller(..$args)" if caller.symbol == futureSym && state in Receive =>
      enter(Future)
    case tree @ q"$caller(..$args)" if caller.symbol == senderSym && state in Future =>
      nok(Warning(tree))
  }
}
