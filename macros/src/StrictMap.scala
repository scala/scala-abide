package scala.reflect.internal.traversal

import scala.collection.GenTraversableOnce
import scala.collection.generic._
import scala.collection.immutable._

/** A map class that uses strict equality for key check */
class StrictMap[A <: AnyRef, +B]
  extends AbstractMap[A,B]
     with Map[A, B]
     with MapLike[A, B, StrictMap[A, B]] {

  protected[StrictMap] val underlying : Map[StrictMap.EqWrapper[A], B] = Map.empty

  override def size : Int = underlying.size

  override def empty = StrictMap.empty[A,B]

  def iterator : Iterator[(A,B)] = underlying.iterator.map { case (k, v) => (k.value -> v) }

  def get(key: A) : Option[B] = underlying.get(new StrictMap.EqWrapper(key))

  override def updated [B1 >: B] (key: A, value: B1): StrictMap[A, B1] = new StrictMap[A,B1]() {
    override protected[StrictMap] val underlying : Map[StrictMap.EqWrapper[A], B1] =
      StrictMap.this.underlying + (new StrictMap.EqWrapper(key) -> value)
  }

  override def + [B1 >: B] (kv: (A, B1)): StrictMap[A, B1] =
    updated(kv._1, kv._2)

  override def + [B1 >: B] (elem1: (A, B1), elem2: (A, B1), elems: (A, B1) *): StrictMap[A, B1] =
    this + elem1 + elem2 ++ elems

  def - (key: A): StrictMap[A, B] = new StrictMap[A,B]() {
    override protected[StrictMap] val underlying : Map[StrictMap.EqWrapper[A], B] =
      StrictMap.this.underlying - new StrictMap.EqWrapper(key)
  }

  override def ++ [B1 >: B] (xs : GenTraversableOnce[(A, B1)]) : StrictMap[A, B1] =
    ((repr : StrictMap[A, B1]) /: xs.seq) (_ + _)
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

