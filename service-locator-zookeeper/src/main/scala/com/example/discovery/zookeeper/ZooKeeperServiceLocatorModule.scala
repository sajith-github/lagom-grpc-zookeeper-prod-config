package com.example.discovery.zookeeper

import com.lightbend.lagom.scaladsl.api.ServiceLocator
import play.api.{Configuration, Environment, Mode}
import play.api.inject.{Binding, Module}

import javax.inject.Singleton

/**
  * This module binds the ServiceLocator interface from Lagom to the `ZooKeeperServiceLocator`.
  * The `ZooKeeperServiceLocator` is only bound if the application has been started in `Prod` mode.
  * In `Dev` mode the embedded service locator of Lagom is used.
  */
class ZooKeeperServiceLocatorModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(bind[ZooKeeperServiceLocator.ZookeeperConfig].toInstance(ZooKeeperServiceLocator.fromConfigurationWithPath(configuration)).in[Singleton]) ++
      (if (environment.mode == Mode.Prod) Seq(bind[ServiceLocator].to[ZooKeeperServiceLocator].in[Singleton])
      else Seq.empty)
  }
}