package org.clark

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props

/**
 * Actor to respond prime service, send prime number computing task to PrimeStream
 * Keep a local cache of prime numbers
 */
class PrimeCache extends Actor with ActorLogging {

  val pst = context.actorOf(Props[PrimeStream], "primeActor")
  private var cache = Cache(2, List(2))

  def checkPrime(i: Int):IsPrime = {
    if (cache.upper >= i) { //already computed
      val all_primes = cache.primes.filter(_ <= i)
      if (all_primes.isEmpty) IsPrime(false,List())
      else if (all_primes.last == i) IsPrime(true, all_primes.dropRight(1))
      else IsPrime(false, all_primes)
    } else {
      IsPrime(isPrime(i))
    }

  }

  def isPrime(i: Int): Boolean = {
    if (i <= 1) return false
    if (i == 2) return true
    if (i % 2 ==0) return false
    val bound = Math.sqrt(i)
    var d = 3
    while (d <= bound) {
      if (i % d == 0) return false
      //choose next d
      cache.primes.find(_ > d) match {
        case None => d += 2 //d must be odd number
        case Some(p) => d = p
      }
    }
    true
  }

  def receive = {
    //request from the prime service
    case CheckPrime(i) =>
      val p=checkPrime(i)
      if (i>1 && p.primes.isEmpty) pst ! CheckPrime(i)
      sender ! p
      
    //receive from PrimStream actor   
    case cc: Cache =>
      if (cache.upper < cc.upper) this.cache = cc

    case _ =>
  }
}

/**
 * Actor that lazily compute the prime numbers on demand and keep the result up to the range from 1 to <upper>
 */
class PrimeStream extends Actor with ActorLogging {

  private var upper = 2

  //stream of consecutive integers
  def from(n: Int): Stream[Int] = n #:: from(n + 1)

  //Sieve of Eratosthenes for primes
  def sieve(s: Stream[Int]): Stream[Int] =
    s.head #:: sieve(s.tail filter (_ % s.head != 0))

  //assign primes to a value so it memorizes the computation 
  val primes = sieve(from(2))  

  def receive = {

    //receive from PrimeCache
    case CheckPrime(i) if i>1 =>
      //val smaller = primes.filter(_ <= i).toList
      //println(primes)
      val smaller = primes.takeWhile { _ <= i }.toList
      upper = Math.max(upper, i)
      sender ! Cache(upper, smaller)
    case _ =>
  }
}

/**
 * case class that contains the largest integer <upper> checked so far and primes less than or equal to the <upper>
 */
case class Cache(upper: Int, primes: List[Int])