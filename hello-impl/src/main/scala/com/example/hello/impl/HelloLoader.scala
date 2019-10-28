package com.example.hello.impl

import com.example.discovery.zookeeper.{ZooKeeperServiceLocator, ZooKeeperServiceRegistry}
import com.example.hello.api.HelloService
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.curator.x.discovery.{ServiceInstance, UriSpec}
import play.api.libs.ws.ahc.AhcWSComponents

class HelloLoader extends LagomApplicationLoader {

  private val config: Config = ConfigFactory.load
  val defaultConfigPath = "lagom.discovery.zookeeper"
  val zKConfig: ZooKeeperServiceLocator.ZookeeperConfig = ZooKeeperServiceLocator
    .fromConfig(config.getConfig(defaultConfigPath))

  val serviceAddress = "127.0.0.1"


  def newServiceInstance(serviceName: String, serviceId: String, servicePort: Int): ServiceInstance[String] = {
    ServiceInstance.builder[String]
      .name(serviceName)
      .id(serviceId)
      .address(serviceAddress)
      .port(servicePort)
      .sslPort(8443)
      .uriSpec(new UriSpec("{scheme}://{serviceAddress}:{servicePort}"))
      .build
  }

  override def load(context: LagomApplicationContext): LagomApplication = {
    val application: HelloApplication = new HelloApplication(context) {
      val locator = new ZooKeeperServiceLocator(zKConfig)
      val registry = new ZooKeeperServiceRegistry(s"${zKConfig.serverHostname}:${zKConfig.serverPort}",
        zKConfig.zkServicesPath)
      registry.start()
      registry.register(newServiceInstance("hello-srvc", "1", 8000))

      override def serviceLocator: ServiceLocator = locator
    }
    application
  }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new HelloApplication(context) with LagomDevModeComponents


  override def describeService = Some(readDescriptor[HelloService])
}

abstract class HelloApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with AhcWSComponents {

  // Bind the service that this server provides
  override lazy val lagomServer =
    serverFor[HelloService](wire[HelloServiceImpl])
      .additionalRouter(wire[HelloGrpcServiceImpl])

}
