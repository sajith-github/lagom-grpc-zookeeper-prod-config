package com.example.helloproxy.impl

import javax.net.ssl._
import play.core.ApplicationProvider
import play.server.api._

class SSLProvider(appProvider: ApplicationProvider) extends SSLEngineProvider {

  override def createSSLEngine(): SSLEngine = {
    // change it to your custom implementation
    println("++++++++++++++++++++++++++= Hello in pro+++++++++++++++++++++++++++++++++")
    //    appProvider.get.
    SSLContext.getDefault.createSSLEngine
    //    SSLContext.
  }

}