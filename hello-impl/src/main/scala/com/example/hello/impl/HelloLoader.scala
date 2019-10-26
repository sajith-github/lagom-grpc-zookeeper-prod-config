package com.example.hello.impl

import com.example.hello.api.HelloService
import com.lightbend.lagom.scaladsl.akka.discovery.AkkaDiscoveryComponents
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import org.apache.curator.x.discovery.{ServiceInstance, UriSpec}
import play.api.libs.ws.ahc.AhcWSComponents

class HelloLoader extends LagomApplicationLoader {

  val serviceAddress = "localhost"
  val serverhost = "localhost"
  val serverport = 2181
  val serverscheme = "http"
  val serverroute = "round-robin"
  val defaultConfigPath = "lagom.discovery.zookeeper"
  val defaultZKServicesPath = "/lagom/services"

  def testConfig(serverHostname: String = serviceAddress,
                 serverPort: Int = serverport,
                 scheme: String = "http",
                 routingPolicy: String = "round-robin",
                 zkServicesPath: String = defaultZKServicesPath): ZooKeeperServiceLocator.Config =
    ZooKeeperServiceLocator.Config(serverHostname = serverHostname,
      serverPort = serverPort,
      scheme = scheme,
      routingPolicy = routingPolicy,
      zkServicesPath = zkServicesPath)


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
      val locator = new ZooKeeperServiceLocator(testConfig())
      val registry = new ZooKeeperServiceRegistry(s"$serverhost:$serverport", defaultZKServicesPath)
      registry.start()
      registry.register(newServiceInstance("hello-srvc", "1", 8000))
      registry.register(newServiceInstance("hello-srvc", "2", 3000))
      registry.register(newServiceInstance("helloworld.GreeterService", "2", 8000))
      val instance = newServiceInstance("helloworld.GreeterService", "3", 8443)
      registry.register(instance)

      override def serviceLocator: ServiceLocator = locator
    }
    application
  }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication = {
    val application: HelloApplication = new HelloApplication(context) {
      val locator = new ZooKeeperServiceLocator(testConfig())
      val registry = new ZooKeeperServiceRegistry(s"$serverhost:$serverport", defaultZKServicesPath)
      registry.start()
      registry.register(newServiceInstance("hello-srvc", "1", 8000))
      //      registry.register(newServiceInstance("hello-srvc", "3", 8443))
      registry.register(newServiceInstance("hello-srvc", "2", 3000))
      val instance = newServiceInstance("helloworld.GreeterService", "2", 8443)
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
