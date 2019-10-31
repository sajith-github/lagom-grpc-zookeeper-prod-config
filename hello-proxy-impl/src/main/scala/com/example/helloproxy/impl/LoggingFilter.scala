package com.example.helloproxy.impl

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.example.helloproxy.api.ExternalService
import javax.inject.Inject
import play.api.mvc.{Filter, RequestHeader, Result}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

class LoggingFilter @Inject()(externalService: ExternalService) extends Filter {

  private implicit val sys: ActorSystem = ActorSystem("ExtractorClientActor")
  implicit val mat: ActorMaterializer = ActorMaterializer()
  private implicit val ec: ExecutionContextExecutor = sys.dispatcher

  def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {

    println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++")
    val result = externalService.countryList.invoke()

    val re = Await.result(result, Duration.Inf)
    println(re)
    println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++")
    nextFilter(requestHeader).map { result => result
    }
  }
}
