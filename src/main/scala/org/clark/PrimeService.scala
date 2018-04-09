package org.clark

import com.typesafe.scalalogging.LazyLogging
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import scala.concurrent.Future
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.Http
import akka.actor.CoordinatedShutdown
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.model.StatusCodes
import akka.dispatch.OnFailure

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val itemFormat = jsonFormat2(IsPrime)
}

case class CheckPrime(i:Int)
case class IsPrime(isPrime: Boolean, primes:List[Int]=List.empty)

object PrimeService extends App with LazyLogging with JsonSupport{
  
  implicit val system = ActorSystem("alert-service")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  
  val primeCache = system.actorOf(Props[PrimeCache], "primeCache")
  implicit val timeout = Timeout(5 seconds)
  
  val route = 
    pathPrefix("primes"/IntNumber) { target =>
      get {       
        if (target > 50000) {
          complete("Please give smaller number.")
        }else {
          val future = ask(primeCache,CheckPrime(target)).mapTo[IsPrime]
          onSuccess(future) {data =>
            logger.info(s"Is $target a prime? - ${data.isPrime}")
            complete(data)
          }
        }
        //complete(future)
      }
  }
  
  val (host, port) = ("localhost", 8080)
  val bindingFuture: Future[ServerBinding] = Http().bindAndHandle(route, host, port)
  logger.info(s"The prime service at $host is started, listening on port :$port")
  CoordinatedShutdown(system).addJvmShutdownHook {
    logger.info("\n start custom akka shutdown before JVM shudown...")
    bindingFuture.flatMap { s => s.unbind() }.onComplete { x => system.terminate() }
  }

}

