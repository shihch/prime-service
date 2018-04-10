package org.clark

import org.scalatest._
import akka.actor.ActorSystem
import akka.testkit.TestActorRef
import akka.testkit.TestKit
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.util.Success

class PrimeTest extends TestKit(ActorSystem("Testsystem")) with FlatSpecLike with Matchers with BeforeAndAfterAll {

  val pcRef = TestActorRef(new PrimeCache)
  val pc = pcRef.underlyingActor
  val psRef = TestActorRef(new PrimeStream)
  val ps = psRef.underlyingActor
  implicit val timeout = Timeout(5 seconds)

  it should "compute correct prime numbers" in {
    assertResult(true)(ps.isPrime(53))

    //different way to compute but same prime numbers
    ps.primes.take(100).foreach { p =>
      assertResult(true)(ps.isPrime(p))
    }
  }

  it should "send check prime msg and respond with correct result" in {
    val future = psRef ? TellPrime(15)
    val Success(result:IsPrime)=future.value.get
    assertResult(false)(result.isPrime)
    
    assertResult(Success(IsPrime(true,List())))((pcRef ? CheckPrime(67)).value.get)
  }

  override def afterAll {
    shutdown()
  }
}