package com.example.hello.impl

import com.example.hello.api.HelloService
import com.lightbend.lagom.discovery.zookeeper.{ZooKeeperServiceLocator, ZooKeeperServiceRegistry}
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import org.apache.curator.x.discovery.{ServiceInstance, UriSpec}
import play.api.libs.ws.ahc.AhcWSComponents

class HelloLoader extends LagomApplicationLoader {

  val serviceAddress = "localhost"

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
      val zookeeperConf: ZooKeeperServiceLocator.ZookeeperConfig = ZooKeeperServiceLocator
        .fromConfigurationWithPath(application.configuration)
      val locator = new ZooKeeperServiceLocator(zookeeperConf)
      val registry = new ZooKeeperServiceRegistry(s"${zookeeperConf.serverHostname}:${zookeeperConf.serverPort}",
        zookeeperConf.zkServicesPath)
      registry.start()
      registry.register(newServiceInstance("hello-srvc", "1", 8000))
//      registry.register(newServiceInstance("hello-srvc", "2", 3000))
      registry.register(newServiceInstance("helloworld.GreeterService", "2", 8000))
      val instance: ServiceInstance[String] = newServiceInstance("helloworld.GreeterService", "3", 8443)
      registry.register(instance)

      override def serviceLocator: ServiceLocator = locator
    }
    application
  }

//  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
//    new HelloApplication(context) with LagomDevModeComponents


  override def loadDevMode(context: LagomApplicationContext): LagomApplication ={
    val application: HelloApplication = new HelloApplication(context) {
      val zookeeperConf: ZooKeeperServiceLocator.ZookeeperConfig = ZooKeeperServiceLocator
        .fromConfigurationWithPath(application.configuration)
      val locator = new ZooKeeperServiceLocator(zookeeperConf)
      val registry = new ZooKeeperServiceRegistry(s"${zookeeperConf.serverHostname}:${zookeeperConf.serverPort}",
        zookeeperConf.zkServicesPath)
      registry.start()
      registry.register(newServiceInstance("hello-srvc", "1", 8000))
      registry.register(newServiceInstance("hello-srvc", "2", 3000))
      registry.register(newServiceInstance("helloworld.GreeterService", "2", 8000))
      val instance: ServiceInstance[String] = newServiceInstance("helloworld.GreeterService", "3", 8443)
      registry.register(instance)

      override def serviceLocator: ServiceLocator = locator
    }
    application
  }


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
