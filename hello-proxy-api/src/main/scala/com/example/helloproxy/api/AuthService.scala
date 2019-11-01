package com.example.helloproxy.api

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import play.api.libs.json.{Format, Json}

/**
  * Created by mohsennavabi on 2017/10/12AD.
  */
trait ExternalService extends Service {

  def countryList: ServiceCall[NotUsed, Countries]


  override def descriptor: Descriptor = {
    import Service._
    named("external-service")
      .withCalls(
        restCall(Method.GET, "/country/get/all", countryList _)
      )
      .withAutoAcl(false)
  }
}

case class Countries(RestResponse: Response)

case class Response(messages: List[String], result: List[Country])

case class Country(name: String, alpha2_code: String, alpha3_code: String)

object Countries {
  implicit val format: Format[Countries] = Json.format[Countries]
}


object Country {
  implicit val format: Format[Country] = Json.format[Country]
}

object Response {
  implicit val format: Format[Response] = Json.format[Response]
}

