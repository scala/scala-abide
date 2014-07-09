package scala.tools.abide.directives

import scala.reflect.internal._
import scala.reflect.internal.util._

import scala.collection.mutable.{Map => MutableMap}

sealed abstract class Witness {
  def isMutable : Boolean
}

abstract class MutableWitness extends Witness {
  val path : Seq[String]
  def isMutable : Boolean = true
  def withPath(path : Seq[String]) : MutableWitness = this match {
    case pvw : PublicVarWitness => pvw.copy(path = path ++ MutableWitness.this.path)
    case emw : ExtendsMutableWitness => emw.copy(path = path ++ MutableWitness.this.path)
  }
}

case class PublicVarWitness(path : Seq[String]) extends MutableWitness {
  override def toString : String = s"${path.mkString(".")} is a public `var`"
}

case class ExtendsMutableWitness(path : Seq[String], tpt : String) extends MutableWitness {
  override def toString : String = s"${path.mkString(".")} is a public `val` of type $tpt <: Mutable"
}

case object NoWitness extends Witness {
  def isMutable : Boolean = false
}

trait MutabilityChecker {

  val universe : SymbolTable

  import universe._
  import universe.definitions._
  import scala.collection.immutable.Set

  private val publicMutableCache : MutableMap[Type, Witness] = MutableMap.empty

  def publicMutable(tpt : Type) : Witness = {
    def rec(tpts : List[(Type, Seq[String])], seen : Set[Type]) : Witness = {
      var nextTpes : List[(Type, Seq[String])] = List.empty
      var nextSeen : Set[Type] = seen

      val witnessOpt = tpts.view.flatMap { case (tpe, path) =>
        val res : Option[MutableWitness] = publicMutableCache.get(tpe) match {
          case Some(witness : MutableWitness) => Some(witness)
          case Some(NoWitness) => None
          case None =>
            val wtpeOpt = if (tpe.typeSymbol.isClass && tpe.typeSymbol.asClass.baseClasses.exists(_ == symbolOf[scala.Mutable])) {
              Some(ExtendsMutableWitness(Seq.empty, tpe.toString))
            } else tpe.members.view.flatMap { member =>
              if (member.isPublic && member.isSetter) { // if member is a var, we are clearly mutable
                Some(PublicVarWitness(Seq(member.accessed.name.toString)))
              } else if (!member.isSynthetic && member.isPublic && member.isVal) {
                // we use the seen set here to manage type cycles (these are immutable since we know the cycle
                // doesn't point to a var, as we've established this above)
                val termType = member.typeSignature
                if (!nextSeen(termType)) {
                  nextSeen  +=  termType
                  nextTpes :+= (termType, path :+ member.name.toString)
                }

                None
              } else {
                None
              }
            }.headOption

            if (wtpeOpt.isDefined) publicMutableCache(tpe) = wtpeOpt.get
            wtpeOpt
        }

        res.map(witness => witness.withPath(path))
      }.headOption

      witnessOpt match {
        case Some(witness) => witness
        case None if nextTpes.isEmpty => NoWitness
        case _ => rec(nextTpes, nextSeen)
      }
    }

    rec(List(tpt -> Seq.empty[String]), Set(tpt))
  }

}
