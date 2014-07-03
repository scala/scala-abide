package scala.tools.abide.directives

import scala.tools.nsc._
import scala.reflect.internal.util._

import scala.collection.mutable.{Map => MutableMap}

trait MutabilityChecker {

  val global : Global

  import global._
  import global.definitions._
  import scala.collection.immutable.Set

  private lazy val mutableTraitSymbol   : Symbol = rootMirror.getClassByName(newTypeName("scala.Mutable"))
  private lazy val immutableTraitSymbol : Symbol = rootMirror.getClassByName(newTypeName("scala.Immutable"))

  private class MutabilityTraverser {
    private val cache : MutableMap[Type, Boolean] = MutableMap.empty

    protected final def traverse(tpt : Type, seen : Set[Type]) : Boolean = cache.get(tpt) match {
      case Some(mutable) => mutable
      case None =>
        val result = itraverse(tpt, seen)
        cache(tpt) = result
        result
    }

    protected def itraverse(tpt : Type, seen : Set[Type]) : Boolean = tpt match {
      case tpe @ TypeRef(_, sym, params) =>
        // if a type parameter is a mutable type, we're probably mutable (eg. tuple._1.mutate)
        if (params.exists(traverse(_, seen + tpt))) {
          true

        // if a parent is immutable, we believe the library writer knows what he's doing and report <immutable>
        } else if (tpe.parents.exists(_.typeSymbol == immutableTraitSymbol)) {
          false

        // if a parent is mutable, we believe the library writer knows what's up and report <mutable>
        } else if (tpe.parents.exists(_.typeSymbol == mutableTraitSymbol)) {
          true

        // otherwise, we have to look at type members
        } else {
          // synthetic members are ignored since they come from the compiler (hopefully nothing important was
          // translated to synthetic members during compilation phases)
          lazy val mutableMember = tpe.members.filter(!_.isSynthetic).exists { member =>
            member.isVar || { // if member is a var, we are clearly mutable
              // we use the seen set here to manage type cycles (these are immutable since we know the cycle
              // doesn't point to a var, as we've established this above)
              val termType = member.typeSignature
              !seen.contains(termType) && traverse(termType, seen + tpt)
            }
          }

          // we can also check direct knwon subclasses for mutability. If we find it, we can probably report
          // the current type as mutable since it could (in most cases) be an instance of the mutable child type
          lazy val mutableChild = if (!sym.isClass) false else {
            sym.asClass.knownDirectSubclasses.exists { sub =>
              sub.isType && traverse(sub.asType.toType, seen + tpt)
            }
          }

          mutableMember || mutableChild
        }

      // probably won't happen in practice as mutability check doesn't make much sense
      // in a class definition, but might as well deal with the case to avoid issues
      case ClassInfoType(_, _, sym) =>
        traverse(sym.tpe, seen + tpt)

      // method fields cannot imply mutability. Period.
      // args -> if mutable and method mutates them, current type isn't mutated unless argument
      //         points to mutable field of current type which will be discovered somewhere else
      // return -> same argument, can only mutate if mutable field is defined somewhere else
      case PolyType(params, method) => false
      case MethodType(args, ret) => false
      case NullaryMethodType(ret) => false

      // check the underlying type for mutability (probably won't take place in practice)
      case ThisType(sym) =>
        val symType = sym.tpe
        !seen.contains(symType) && traverse(symType, seen + tpt)

      // check parents and new definitions, like in a TypeRef
      case RefinedType(parents, defs) =>
        val mutableParent = parents.exists(traverse(_, seen + tpt))
        val mutableField = defs.exists(d => traverse(d.tpe, seen + tpt))
        mutableParent || mutableField

      // not too sure about this one, can existential symbols imply mutability?
      case ExistentialType(symbols, tpe) =>
        traverse(tpe, seen + tpt)

      case TypeBounds(lo, hi) => traverse(lo, seen + tpt) || traverse(hi, seen + tpt)

      case AnnotatedType(_, tpe) => traverse(tpe, seen + tpt)

      case tpe : SingletonType => false // TODO ??

      // should never happen, and can't imply mutability in any case
      case ImportType(expr) => false

      case ErrorType => false

      case NoType => false

      case null => scala.sys.error("Tried to check type mutability on `null` type")

      case tpe => scala.sys.error("Unknown type: " + tpe + " : " + tpe.getClass)
    }

    final def traverse(tpt : Type) : Boolean = traverse(tpt, Set.empty)
  }

  private val mutableTraverser = new MutabilityTraverser

  def mutable(tpt : Type) : Boolean = mutableTraverser.traverse(tpt)

  private class PublicMutabilityTraverser extends MutabilityTraverser {
    override def itraverse(tpt : Type, seen : Set[Type]) : Boolean = {
      tpt.members.filter(m => !m.isSynthetic && m.isPublic).exists { member =>
        member.isVar || { // if member is a var, we are clearly mutable
          // we use the seen set here to manage type cycles (these are immutable since we know the cycle
          // doesn't point to a var, as we've established this above)
          val termType = member.typeSignature
          !seen.contains(termType) && traverse(termType, seen + tpt)
        }
      }
    }
  }

  private val publicMutableTraverser = new PublicMutabilityTraverser

  def publicMutable(tpt : Type) : Boolean = publicMutableTraverser.traverse(tpt)

  def isMutator(tree: Tree) : Boolean = {
    def rec(tree: Tree) : Boolean = tree match {
      case Apply(lhs, args) =>
        println("apply="+tree)
        tree.children.exists(rec(_))
      case Select(lhs, name) =>
        println("select="+tree)
        if (tree.symbol.isSetter) {
          true
        } else 

        if (tree.symbol.isMethod && !tree.symbol.isGetter && !tree.symbol.isSetter) {
          
        }
        println(tree.symbol.isMethod + " " + tree.symbol.isGetter + " " + tree.symbol.isSetter)
        tree.children.exists(rec(_))
      case _ =>
        tree.children.exists(rec(_))
    }

    rec(tree)
  }

  def isMutableBlock(tree: Tree): Boolean = {
    true
  }

  def isMutable(tree: Tree): Boolean = {
    false
  }
}
