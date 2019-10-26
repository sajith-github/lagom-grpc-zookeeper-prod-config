package com.example.helloproxy.impl

import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.grpc.GrpcClientSettings
import com.example.hello.api.HelloService
import com.example.helloproxy.api.HelloProxyService
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import example.myapp.helloworld.grpc.{GreeterService, GreeterServiceClient}
import org.apache.curator.x.discovery.{ServiceInstance, UriSpec}
import play.api.libs.ws.ahc.AhcWSComponents

import scala.concurrent.ExecutionContextExecutor

class HelloProxyLoader extends LagomApplicationLoader {

  val serviceAddress = "127.0.0.1"
  val serverhost = "127.0.0.1"
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
      //      .sslPort(9443)
      .uriSpec(new UriSpec("{scheme}://{serviceAddress}:{servicePort}"))
      .build
  }

  override def load(context: LagomApplicationContext): LagomApplication = {
    val application: HelloProxyApplication = new HelloProxyApplication(context) {
      val locator = new ZooKeeperServiceLocator(testConfig())
      val registry = new ZooKeeperServiceRegistry(s"$serverhost:$serverport", defaultZKServicesPath)
      registry.start()
      registry.register(newServiceInstance("hello-proxy", "1", 9000))
      registry.register(newServiceInstance("hello-proxy", "2", 9443))
      //      registry.register(newServiceInstance("hello-proxy", "3", 9000))

      override def serviceLocator: ServiceLocator = locator
    }
    application
  }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication = {
    val application: HelloProxyApplication = new HelloProxyApplication(context) {
      val locator = new ZooKeeperServiceLocator(testConfig())
      val registry = new ZooKeeperServiceRegistry(s"$serverhost:$serverport", defaultZKServicesPath)
      registry.start()
      registry.register(newServiceInstance("hello-proxy", "1", 3000))
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

//  private lazy val settings = GrpcClientSettings
  //    .usingServiceDiscovery(GreeterService.name)
  //    .withServicePortName("https")
  //    .withDeadline(Duration.create(5, TimeUnit.SECONDS)) // response timeout
  //    .withConnectionAttempts(5) // use a small reconnectionAttempts value to cause a client reload in case of failure
  //

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

