package com.typesafe.abide.akka

import scala.tools.abide._
import scala.tools.abide.traversal._

class ClosingOverContext(val context: Context) extends ScopingRule with SimpleWarnings {
  import context.universe._

  val warning = w"Closing over Actor.context value in callback at $tree"

  type Owner = Symbol

  lazy val actorSym = rootMirror.getClassByName(TypeName("akka.actor.Actor"))
  lazy val contextSym = actorSym.toType.members.find(_.name == TermName("context"))

  lazy val flowSym = rootMirror.getClassByName(TypeName("akka.stream.scaladsl.Flow"))
  lazy val onCompleteSym = flowSym.toType.members.find(_.name == TermName("onComplete"))

  val step = optimize {
    case tree @ q"$caller(..$mat)(..$cb)" if onCompleteSym.map(caller.symbol.==).getOrElse(false) =>
      enter(caller.symbol)
    case s: Select if contextSym.map(s.symbol.==).getOrElse(false) && state.parent.isDefined =>
      nok(s)
  }
}
