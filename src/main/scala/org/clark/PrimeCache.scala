package org.clark

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.{ask,pipe}
import scala.util.Success

/**
 * Actor to respond prime service, send prime number computing task to PrimeStream
 * Keep a local cache of prime numbers
 */
class PrimeCache extends Actor with ActorLogging {

  val pst = context.actorOf(Props[PrimeStream], "primeActor")
  implicit val executionContext = context.dispatcher
  implicit val timeout = Timeout(5 seconds)
  private var upper = Upper(2)

  def receive = {
    case CheckPrime(i) =>
      if (upper.bound < i) { //the number was not computed before
        val fut = pst ? TellPrime(i)
        pst ! CheckPrime(i) //send to PrimeStream to compute in the background       
        fut.mapTo[IsPrime] pipeTo sender       
      }else {
        (pst ? GetPrimes(i)).mapTo[IsPrime] pipeTo sender
      }
    case u:Upper => this.upper = u 
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
  
  def takePrimes(upto:Int) = {
    primes.takeWhile { _ <= upto }.toList
  }
  
  def isPrime(i: Int): Boolean = {
    if (i <= 1) return false
    if (i == 2) return true
    if (i % 2 ==0) return false
    val bound = Math.sqrt(i).toInt
    val test_primes = takePrimes(Math.min(bound,upper)) //only take computed
    var d = 3    
    while (d <= bound) {
      if (i % d == 0) return false
      //choose next d
      test_primes.find( _ > d) match {
        case None => d += 2 //d must be odd number
        case Some(p) => d = p
      }
    }
    true
  }

  def receive = {

    //receive from PrimeCache
    case CheckPrime(i) if i>1 =>
      //val smaller = primes.filter(_ <= i).toList
      //println(primes)
      val smaller = primes.takeWhile { _ <= i }.toList
      upper = Math.max(upper, i)
      //sender ! Cache(upper, smaller)
      sender ! Upper(upper)
    case TellPrime(i) if i>1 =>
      sender ! IsPrime(isPrime(i))
    case GetPrimes(i) if i>1 =>
      val all_primes = takePrimes(i)
      val ps=if (all_primes.isEmpty) IsPrime(false,List())
        else if (all_primes.last == i) IsPrime(true, all_primes.dropRight(1))
        else IsPrime(false, all_primes)
      sender ! ps
    case _ =>
  }
}

/**
 * case class that contains the largest integer checked so far.
 */
case class Upper(bound:Int)
case class TellPrime(i:Int)
case class GetPrimes(i:Int)

