package com.example.helloproxy.impl

import java.io.Closeable
import java.net.{InetAddress, URI}

import com.lightbend.lagom.scaladsl.api.{Descriptor, ServiceLocator}
import com.typesafe.config.Config
import com.typesafe.config.ConfigException.BadValue
import javax.inject.Inject
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.utils.CloseableUtils
import org.apache.curator.x.discovery.{ServiceDiscovery, ServiceDiscoveryBuilder, ServiceInstance}
import play.api.Configuration

import scala.collection.JavaConverters._
import scala.collection.concurrent
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random


trait ZooKeeperServiceLocatorConfig {
  def serverHostname: String

  def serverPort: Int

  def scheme: String

  def routingPolicy: String

  def zkServicesPath: String

  def zkUri: String
}


object ZooKeeperServiceLocator {

  case class ZookeeperConfig(serverHostname: String,
                             serverPort: Int,
                             scheme: String, // Technically should be another type
                             routingPolicy: String, // Technically should be another type
                             zkServicesPath: String) extends ZooKeeperServiceLocatorConfig {
    def zkUri: String = ZooKeeperServiceLocator.zkUri(serverHostname, serverPort)
  }

  def javaConfig(serverHostname: String,
                 serverPort: Int,
                 scheme: String,
                 routingPolicy: String,
                 zkServicesPath: String): ZooKeeperServiceLocatorConfig =
    ZookeeperConfig(serverHostname,
      serverPort,
      scheme,
      routingPolicy,
      zkServicesPath)

  def fromConfigurationWithPath(in: Configuration, path: String = defaultConfigPath): ZookeeperConfig = {
    fromConfiguration(in.underlying.atPath(defaultConfigPath))
  }

  def fromConfiguration(in: Config): ZookeeperConfig = fromConfig(in)

  def fromConfig(in: Config): ZookeeperConfig =
    ZookeeperConfig(serverHostname = in.getString("server-hostname"),
      serverPort = in.getInt("server-port"),
      scheme = in.getString("uri-scheme"),
      routingPolicy = in.getString("routing-policy"),
      zkServicesPath = defaultZKServicesPath)

  val defaultConfigPath = "lagom.discovery.zookeeper"
  val defaultZKServicesPath = "/lagom/services"

  def zkUri(serverHostname: String, serverPort: Int): String = s"$serverHostname:$serverPort"

}

class ZooKeeperServiceLocator(serverHostname: String,
                              serverPort: Int,
                              scheme: String,
                              routingPolicy: String,
                              zkServicesPath: String,
                              zkUri: String)(implicit ec: ExecutionContext) extends ServiceLocator with Closeable {


  @Inject()
  def this(config: ZooKeeperServiceLocator.ZookeeperConfig)(implicit ec: ExecutionContext) =
    this(config.serverHostname,
      config.serverPort,
      config.scheme,
      config.routingPolicy,
      config.zkServicesPath,
      config.zkUri)(ec)

  private val zkClient: CuratorFramework =
    CuratorFrameworkFactory.newClient(zkUri, new ExponentialBackoffRetry(1000, 3))
  zkClient.start()

  private val serviceDiscovery: ServiceDiscovery[String] =
    ServiceDiscoveryBuilder
      .builder(classOf[String])
      .client(zkClient)
      .basePath(zkServicesPath)
      .build()
  serviceDiscovery.start()

  private val roundRobinIndexFor: concurrent.Map[String, Int] = new concurrent.TrieMap[String, Int]

  private def locateAsScala(name: String): Future[Option[URI]] = {
    val instances: List[ServiceInstance[String]] = serviceDiscovery.queryForInstances(name).asScala.toList
    Future {
      instances.size match {
        case 0 => None
        case 1 => toURIs(instances).headOption
        case _ =>
          routingPolicy match {
            case "first" => Some(pickFirstInstance(instances))
            case "random" => Some(pickRandomInstance(instances))
            case "round-robin" => Some(pickRoundRobinInstance(name, instances))
            case unknown => throw new BadValue("lagom.discovery.zookeeper.routing-policy", s"[$unknown] is not a valid routing algorithm")
          }
      }
    }
  }


  private def pickFirstInstance(services: List[ServiceInstance[String]]): URI = {
    assert(services.size > 1)
    toURIs(services).sortWith(_.toString < _.toString).head
  }


  private def pickRandomInstance(services: List[ServiceInstance[String]]): URI = {
    assert(services.size > 1)
    toURIs(services).sortWith(_.toString < _.toString).apply(Random.nextInt(services.size - 1))
  }

  private def pickRoundRobinInstance(name: String, services: List[ServiceInstance[String]]): URI = {
    assert(services.size > 1)
    roundRobinIndexFor.putIfAbsent(name, 0)
    val sortedServices = toURIs(services).sortWith(_.toString < _.toString)
    val currentIndex = roundRobinIndexFor(name)
    val nextIndex =
      if (sortedServices.size > currentIndex + 1) currentIndex + 1
      else 0
    roundRobinIndexFor += (name -> nextIndex)
    sortedServices(currentIndex)
  }

  private def toURIs(services: List[ServiceInstance[String]]): List[URI] =
    services.map { service =>
      val address = service.getAddress
      val serviceAddress =
        if (address == "" || address == "localhost") InetAddress.getLoopbackAddress.getHostAddress
        else address
      new URI(s"$scheme://$serviceAddress:${service.getPort}")
    }

  override def locate(name: String, serviceCall: Descriptor.Call[_, _]): Future[Option[URI]] = {
    locateAsScala(name)
  }


  override def doWithService[T](name: String, serviceCall: Descriptor.Call[_, _])(block: URI => Future[T])
                               (implicit ec: ExecutionContext): Future[Option[T]] = {
    locateAsScala(name).flatMap {
      case Some(uri) => block(uri).map(Some.apply)
      case None => Future.successful(None)
    }

  }


  override def close(): Unit = {
    CloseableUtils.closeQuietly(serviceDiscovery)
    CloseableUtils.closeQuietly(zkClient)
  }

}