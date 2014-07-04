package scala.reflect.internal.traversal

import scala.collection.GenTraversableOnce
import scala.collection.generic._
import scala.collection.mutable._

/** A map class that uses strict equality for key check */
class StrictMap[A <: AnyRef, B]
  extends AbstractMap[A,B]
     with Map[A, B]
     with MapLike[A, B, StrictMap[A, B]] {

  protected[StrictMap] val underlying : Map[StrictMap.EqWrapper[A], B] = Map.empty

  override def size : Int = underlying.size
  override def clear() { underlying.clear() }
  override def empty = StrictMap.empty[A,B]

  override def contains(key : A) : Boolean = underlying.contains(new StrictMap.EqWrapper(key))
  override def apply(key : A) : B = underlying.apply(new StrictMap.EqWrapper(key))

  def iterator : Iterator[(A,B)] = underlying.iterator.map { case (k, v) => (k.value -> v) }

  def get(key: A) : Option[B] = underlying.get(new StrictMap.EqWrapper(key))

  def += (kv : (A, B)) : this.type = {
    underlying += (new StrictMap.EqWrapper(kv._1) -> kv._2)
    this
  }

  def -= (key : A) : this.type = {
    underlying -= new StrictMap.EqWrapper(key)
    this
  }
}

object StrictMap {
  import scala.collection.mutable.{ Builder, MapBuilder }

  type Coll = StrictMap[_ <: AnyRef, _]

  private[StrictMap] class EqWrapper[W <: AnyRef](val value : W) {
    override def equals(that : Any) : Boolean = that match {
      case e : EqWrapper[_] => value eq e.value
      case _ => false
    }

    override def hashCode : Int = value.hashCode
  }

  def newBuilder[A <: AnyRef, B] : Builder[(A, B), StrictMap[A, B]] = new MapBuilder[A, B, StrictMap[A, B]](empty[A, B])

  private class StrictMapCanBuildFrom[A <: AnyRef, B] extends CanBuildFrom[Coll, (A, B), StrictMap[A,B]] {
    def apply(from: Coll) = newBuilder[A,B]
    def apply() = newBuilder
  }

  implicit def canBuildFrom[A <: AnyRef, B]: CanBuildFrom[StrictMap[A,B], (A, B), StrictMap[A, B]] =
    new StrictMapCanBuildFrom[A, B]

  def empty[A <: AnyRef,B] : StrictMap[A,B] = EmptyStrictMap.asInstanceOf[StrictMap[A,B]]

  private object EmptyStrictMap extends StrictMap[AnyRef, Nothing]

  def apply[A <: AnyRef,B](tuples: (A,B)*) : StrictMap[A,B] = new StrictMap[A,B]() ++ tuples
}

