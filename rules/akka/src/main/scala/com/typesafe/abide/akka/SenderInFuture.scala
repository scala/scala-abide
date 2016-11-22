package com.lightbend.abide.akka

import scala.tools.abide._
import scala.tools.abide.traversal._

class SenderInFuture(val context: Context) extends PathRule {
  import context.universe._

  val name = "sender-in-future"

  case class Warning(tree: Tree) extends RuleWarning {
    val pos = tree.pos
    val message = s"sender() is not stable and should not be accessed in non-thread-safe environments"
  }

  sealed abstract class Element
  case class Receive(sym: Symbol) extends Element
  case object Future extends Element

  lazy val actorSym = rootMirror.getClassByName(TypeName("akka.actor.Actor"))

  lazy val receiveSym = actorSym.toType.members.find(_.name == TermName("receive")).get
  lazy val senderSym = actorSym.toType.members.find(_.name == TermName("sender")).get

  lazy val futureSym = {
    val concurrentSym = rootMirror.getModuleByName(TermName("scala.concurrent"))
    concurrentSym.moduleClass.toType.members.find(_.name == TermName("future")).get
  }

  val step = optimize {
    case dd @ q"def receive : $tpt = $rhs" if dd.symbol.overrides.exists(_ == receiveSym) =>
      enter(Receive(dd.symbol.owner))
    case q"$caller(..$args)" if caller.symbol == futureSym => state.last match {
      case Some(Receive(sym)) => enter(Future)
      case _                  =>
    }
    case tree @ Apply(sender @ Select(actor, TermName("sender")), _) if sender.symbol == senderSym =>
      if (state matches (Receive(actor.symbol), Future)) {
        nok(Warning(tree))
      }
  }
}
