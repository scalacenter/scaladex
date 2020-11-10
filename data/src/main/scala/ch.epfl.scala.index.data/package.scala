package ch.epfl.scala.index

import java.nio.file.{Files, Path}

package object data {

  def innerJoin[K, A, B, Z](m1: Map[K, A], m2: Map[K, B])(
      f: (A, B) => Z
  ): Map[K, Z] = {
    m1.flatMap { case (k, a) =>
      m2.get(k).map(b => Map(k -> f(a, b))).getOrElse(Map.empty[K, Z])
    }
  }

  def upsert[K, V](map: Map[K, Seq[V]], k: K, v: V) = {
    map.get(k) match {
      case Some(vs) => map.updated(k, vs :+ v)
      case None => map.updated(k, Seq(v))
    }
  }

  def upserts[K, V](map: Map[K, Set[V]], k: K, vs: Set[V]) = {
    map.get(k) match {
      case Some(xs) => map.updated(k, vs ++ xs)
      case None => map.updated(k, vs)
    }
  }

  def fullOuterJoin[K, A, B, Z](m1: Map[K, A], m2: Map[K, B])(
      f: (A, B) => Z
  )(da: A => Z)(db: B => Z): Map[K, Z] = {
    val km1 = m1.keySet
    val km2 = m2.keySet

    (km2 -- km1).map(k => k -> db(m2(k))).toMap ++ // missing in m1
      (km1 -- km2).map(k => k -> da(m1(k))).toMap ++ // missing in m2
      (km1.intersect(km2)).map(k => k -> f(m1(k), m2(k))) // in m1 and m2
  }

  def slurp(path: Path): String = {
    Files.readAllLines(path).toArray.mkString("\n")
  }
}
