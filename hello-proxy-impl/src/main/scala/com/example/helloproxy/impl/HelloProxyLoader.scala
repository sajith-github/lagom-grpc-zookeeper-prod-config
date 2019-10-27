package com.example.helloproxy.impl

import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.grpc.GrpcClientSettings
import com.example.hello.api.HelloService
import com.example.helloproxy.api.HelloProxyService
import com.lightbend.lagom.discovery.zookeeper.{ZooKeeperServiceLocator, ZooKeeperServiceRegistry}
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import example.myapp.helloworld.grpc.{GreeterService, GreeterServiceClient}
import org.apache.curator.x.discovery.{ServiceInstance, UriSpec}
import play.api.libs.ws.ahc.AhcWSComponents

import scala.concurrent.ExecutionContextExecutor

class HelloProxyLoader extends LagomApplicationLoader {
  val serviceAddress = "localhost"

  def newServiceInstance(serviceName: String, serviceId: String, servicePort: Int): ServiceInstance[String] = {
    ServiceInstance.builder[String]
      .name(serviceName)
      .id(serviceId)
      .address(serviceAddress)
      .port(servicePort)
      //      .sslPort(9443)
      .uriSpec(new UriSpec("{scheme}://{serviceAddress}:{servicePort}"))
      .build
  }

  override def load(context: LagomApplicationContext): LagomApplication = {
    val application: HelloProxyApplication = new HelloProxyApplication(context) {
      val zookeeperConf: ZooKeeperServiceLocator.ZookeeperConfig = ZooKeeperServiceLocator
        .fromConfigurationWithPath(application.configuration)
      val locator = new ZooKeeperServiceLocator(zookeeperConf)
      val registry = new ZooKeeperServiceRegistry(s"${zookeeperConf.serverHostname}:${zookeeperConf.serverPort}",
        zookeeperConf.zkServicesPath)
      registry.start()
      registry.register(newServiceInstance("hello-proxy", "1", 9000))
      registry.register(newServiceInstance("hello-proxy", "2", 9443))
      //      registry.register(newServiceInstance("hello-proxy", "3", 9000))

      override def serviceLocator: ServiceLocator = locator
    }
    application
  }

  //  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
  //    new HelloProxyApplication(context) with LagomDevModeComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication = {
    val application: HelloProxyApplication = new HelloProxyApplication(context) {
      val zookeeperConf: ZooKeeperServiceLocator.ZookeeperConfig = ZooKeeperServiceLocator
        .fromConfigurationWithPath(application.configuration)
      val locator = new ZooKeeperServiceLocator(zookeeperConf)
      val registry = new ZooKeeperServiceRegistry(s"${zookeeperConf.serverHostname}:${zookeeperConf.serverPort}",
        zookeeperConf.zkServicesPath)
      registry.start()
      registry.register(newServiceInstance("hello-proxy", "1", 9000))
      registry.register(newServiceInstance("hello-proxy", "2", 9443))
      //      registry.register(newServiceInstance("hello-proxy", "3", 9000))

      override def serviceLocator: ServiceLocator = locator
    }
    application
  }


  override def describeService = Some(readDescriptor[HelloProxyService])
}

abstract class HelloProxyApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with AhcWSComponents {

  private implicit val dispatcher: ExecutionContextExecutor = actorSystem.dispatcher
  private implicit val sys: ActorSystem = actorSystem

  private lazy val settings = GrpcClientSettings.fromConfig(GreeterService.name)

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

