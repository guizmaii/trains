package com.deromefintech.trains

import scalax.collection.Graph
import scalax.collection.edge.WDiEdge

import scala.collection.mutable.ListBuffer
import cats.implicits._

final class RouteService(val routes: Graph[Char, WDiEdge]) {

  type NodeSeq = Vector[Char]

  implicit class ShowOptionalDistance(opt: Option[Int]) {
    def show: String = opt.map(_.toString).getOrElse("NO SUCH ROUTE")
  }

  def n(outer: Char): routes.NodeT = routes get outer

  val addArity2 = (a: Int, b: Int) ⇒ a + b
  val selectLast: Char => NodeSeq => Boolean = t => ns => ns.last == t

  def shortestDistinct(x: Char, y: Char): Option[Int] =
    n(x).shortestPathTo(n(y)).map(_.weight.toInt)

  def shortestSame(x: Char): Option[Int] = {
    // requirements do not accept a value of 0 here and insist on doing a non-trivial cycle
    val lengths =
      for {
        succ <- n(x).diSuccessors
        length <- (shortestDistinct(succ, x), getOneHopDistance(x, succ)) mapN addArity2
      } yield length
    if (lengths.isEmpty) None else Some(lengths.min)
  }

  def shortestRoute(s: Char, t: Char): Option[Int] =
    if (s == t) shortestSame(s)
    else shortestDistinct(s, t)

  def getOneHopDistance(s: Char, t: Char): Option[Int] =
    (n(s) findOutgoingTo n(t)).map(_.weight.toInt)

  // all we are doing is using the Graph API to construct a valid walk if input is valid and ask API to give us back the weight.
  def getDistance(walk: Seq[Char]): Option[Int] = {
    if (walk.lengthCompare(2) < 0) None
    else {
      val walkBuilder = routes.newWalkBuilder(n(walk.head))(walk.length)
      if (walk.tail.forall { x => walkBuilder add n(x) })
        Some(walkBuilder.result.weight.toInt)
      else None
    }
  }

  def findWalksExact(u: Char, limit: Int, p: NodeSeq => Boolean = _ => true): List[NodeSeq] = {
    def explore(u: Char, limit: Int): List[NodeSeq] = {
      (1 to limit).foldLeft(List(Vector(u))) { case (eligibleWalks, _) =>
        for {
          walk <- eligibleWalks
          successor <- n(walk.last).diSuccessors.toList
          newWalk = walk :+ successor.toOuter
        } yield newWalk
      }
    }

    if (limit == 0) Nil // need to suppress walks of length 0
    else explore(u, limit).filter(p)
  }

  lazy val combinePredWithNonZeroLength: (NodeSeq => Boolean) => NodeSeq => Boolean =
    predicate => path => path.lengthCompare(1) > 0 && predicate(path)

  def findWalksMaxHops(u: Char, limit: Int, p: NodeSeq => Boolean = _ => true): List[NodeSeq] = {
    val (totalWalks, _) =
      (1 to limit).foldLeft((List(Vector(u)), List(Vector(u)))) { case ((walks, currGenWalks), _) =>
        val nextGenWalks =
          for {
            walk <- currGenWalks
            next <- n(walk.last).diSuccessors
          } yield walk :+ next.toOuter
        (walks ++ nextGenWalks, nextGenWalks)
      }
    totalWalks.filter(combinePredWithNonZeroLength(p))
  }

  def exploreWalksWithinDistance(u: Char, limit: Int, predicate: NodeSeq => Boolean = _ => true): Seq[NodeSeq] = {
    // this is BFS algorithm and interestingly the library supports it natively but seems to refuse to walk through cycles
    // for a while as is required here.
    val allWalks = ListBuffer(Vector(u))
    var eligibleWalks: Seq[NodeSeq] = List(Vector(u))
    while (eligibleWalks.nonEmpty) {
      val nextEligibleWalks =
        for {
          walk <- eligibleWalks
          successor <- n(walk.last).diSuccessors.toList
          newWalk = walk :+ successor.toOuter
          newWalkDistance <- getDistance(newWalk) if newWalkDistance < limit
        } yield newWalk

      eligibleWalks = nextEligibleWalks
      allWalks.appendAll(eligibleWalks)
    }
    allWalks.filter(combinePredWithNonZeroLength(predicate))
  }

  def findWalksExactSelectLast(s: Char, t: Char, limit: Int): List[NodeSeq] =
    findWalksExact(s, limit, selectLast(t))

  def findWalksMaxHopsSelectLast(s: Char, t: Char, limit: Int): List[NodeSeq] =
    findWalksMaxHops(s, limit, selectLast(t))

  def exploreWalksWithinDistanceSelectLast(s: Char, t: Char, limit: Int): Seq[NodeSeq] =
    exploreWalksWithinDistance(s, limit, selectLast(t))
}
