package com.typesafe.abide.akka

import scala.tools.abide._
import scala.tools.abide.traversal._

class ClosingOverContext(val context: Context) extends PathRule {
  import context.universe._

  val name = "closing-over-context"

  case class Warning(tree: Tree) extends RuleWarning {
    val pos = tree.pos
    val message = s"Closing over Actor.context value in callback at $tree"
  }

  type Element = Symbol

  lazy val actorSym = rootMirror.getClassByName(TypeName("akka.actor.Actor"))
  lazy val contextSym = actorSym.toType.members.find(_.name == TermName("context"))

  lazy val flowSym = rootMirror.getClassByName(TypeName("akka.stream.scaladsl.Flow"))
  lazy val onCompleteSym = flowSym.toType.members.find(_.name == TermName("onComplete"))

  val step = optimize {
    case tree @ q"$caller(..$mat)(..$cb)" if onCompleteSym.map(caller.symbol.==).getOrElse(false) =>
      enter(caller.symbol)
    case s: Select if contextSym.map(s.symbol.==).getOrElse(false) && state.last.isDefined =>
      nok(Warning(s))
  }
}
