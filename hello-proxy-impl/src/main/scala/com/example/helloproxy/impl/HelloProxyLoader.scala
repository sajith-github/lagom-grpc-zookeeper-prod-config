package com.example.helloproxy.impl

import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.grpc.GrpcClientSettings
import com.example.discovery.zookeeper.{ZooKeeperServiceLocator, ZooKeeperServiceRegistry}
import com.example.hello.api.HelloService
import com.example.helloproxy.api.HelloProxyService
import com.example.discovery.zookeeper.ZooKeeperServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import com.typesafe.config.{Config, ConfigFactory}
import example.myapp.helloworld.grpc.{GreeterService, GreeterServiceClient}
import org.apache.curator.x.discovery.{ServiceInstance, UriSpec}
import play.api.libs.ws.ahc.AhcWSComponents

import scala.concurrent.ExecutionContextExecutor
import scala.util.Random

class HelloProxyLoader extends LagomApplicationLoader {

  private val config: Config = ConfigFactory.load
  val defaultConfigPath = "lagom.discovery.zookeeper"
  val zKConfig: ZooKeeperServiceLocator.ZookeeperConfig = ZooKeeperServiceLocator
    .fromConfig(config.getConfig(defaultConfigPath))

  val serviceAddress = "127.0.0.1"
  val sslPort: Int = config.getInt("play.server.https.port")
  val servicePort: Int = config.getInt("play.server.http.port")
  val random: Random.type = scala.util.Random

  def newServiceInstance(serviceName: String, serviceId: String, servicePort: Int): ServiceInstance[String] = {
    ServiceInstance.builder[String]
      .name(serviceName)
      .id(serviceId)
      .address(serviceAddress)
      .port(servicePort)
      .sslPort(sslPort)
      .uriSpec(new UriSpec("{scheme}://{serviceAddress}:{servicePort}"))
      .build
  }

  override def load(context: LagomApplicationContext): LagomApplication = {
    val application: HelloProxyApplication = new HelloProxyApplication(context) {
      val locator = new ZooKeeperServiceLocator(zKConfig)
      val registry = new ZooKeeperServiceRegistry(s"${zKConfig.serverHostname}:${zKConfig.serverPort}",
        zKConfig.zkServicesPath)
      registry.start()
      registry.register(newServiceInstance(serviceInfo.serviceName, s"${random.nextInt}", servicePort))

      override def serviceLocator: ServiceLocator = locator
    }
    application
  }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new HelloProxyApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[HelloProxyService])
}

abstract class HelloProxyApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with AhcWSComponents {

  private implicit val dispatcher: ExecutionContextExecutor = actorSystem.dispatcher
  private implicit val sys: ActorSystem = actorSystem

  private lazy val settings = GrpcClientSettings
    .fromConfig(GreeterService.name)


  lazy val greeterServiceClient: GreeterServiceClient = GreeterServiceClient(settings)
  //  Register a shutdown task to release resources of the client
  coordinatedShutdown
    .addTask(
      CoordinatedShutdown.PhaseServiceUnbind,
      "shutdown-greeter-service-grpc-client"
    ) { () => greeterServiceClient.close() }

  lazy val helloService = serviceClient.implement[HelloService]

  override lazy val lagomServer = serverFor[HelloProxyService](wire[HelloProxyServiceImpl])

}

