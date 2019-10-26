import akka.grpc.gen.scaladsl.play.{PlayScalaClientCodeGenerator, PlayScalaServerCodeGenerator}
import com.lightbend.lagom.core.LagomVersion

organization in ThisBuild := "com.example"
version in ThisBuild := "1.0-SNAPSHOT"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.12.8"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.3.0" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.4" % Test

val lagomGrpcTestkit = "com.lightbend.play" %% "lagom-scaladsl-grpc-testkit" % "0.7.0" % Test
val curator = "org.apache.curator" % "curator-x-discovery" % "2.12.0"
val akkaDicovery = "com.lightbend.lagom" %% "lagom-scaladsl-akka-discovery-service-locator" % LagomVersion.current

lagomServiceEnableSsl in ThisBuild := true
val `hello-impl-HTTPS-port` = 8443


// ALL SETTINGS HERE ARE TEMPORARY WORKAROUNDS FOR KNOWN ISSUES OR WIP
def workaroundSettingsAPI: Seq[sbt.Setting[_]] = Seq(
  // Lagom still can't register a service under the gRPC name so we hard-code 
  // the port and use the value to add the entry on the Service Registry
  lagomServiceHttpsPort := `hello-impl-HTTPS-port`,
  lagomServiceHttpPort := 8000
)

lazy val `lagom-scala-grpc-example` = (project in file("."))
  .aggregate(`hello-api`, `hello-impl`, `hello-proxy-api`, `hello-proxy-impl`)

lazy val `hello-api` = (project in file("hello-api"))
  .settings(
    libraryDependencies += lagomScaladslApi
  )

lazy val `hello-impl` = (project in file("hello-impl"))
  .enablePlugins(LagomScala)
  .enablePlugins(AkkaGrpcPlugin) // enables source generation for gRPC
  .enablePlugins(PlayAkkaHttp2Support) // enables serving HTTP/2 and gRPC
  .settings(
  akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala),
  akkaGrpcGeneratedSources :=
    Seq(
      AkkaGrpc.Server,
      AkkaGrpc.Client // the client is only used in tests. See https://github.com/akka/akka-grpc/issues/410
    ),
  akkaGrpcExtraGenerators in Compile += PlayScalaServerCodeGenerator,
).settings(
  workaroundSettingsAPI: _*
).settings(
  libraryDependencies ++= Seq(
    lagomScaladslTestKit,
    macwire,
    scalaTest,
    lagomGrpcTestkit,
    curator,
    akkaDicovery
  )
).settings(lagomForkedTestSettings: _*)
  .dependsOn(`hello-api`)


def workaroundSettingsProxy: Seq[sbt.Setting[_]] = Seq(
  // Lagom still can't register a service under the gRPC name so we hard-code
  // the port and use the value to add the entry on the Service Registry
  lagomServiceHttpsPort := 9443,
  lagomServiceHttpPort := 3000
)


lazy val `hello-proxy-api` = (project in file("hello-proxy-api"))
  .settings(
    libraryDependencies += lagomScaladslApi
  )

lazy val `hello-proxy-impl` = (project in file("hello-proxy-impl"))
  .enablePlugins(LagomScala)
  .enablePlugins(AkkaGrpcPlugin) // enables source generation for gRPC
  .enablePlugins(JavaAgent)
  .enablePlugins(PlayAkkaHttp2Support)
  .settings(
  akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala),
  akkaGrpcExtraGenerators += PlayScalaClientCodeGenerator,
).settings(workaroundSettingsProxy: _*)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslTestKit,
      macwire,
      scalaTest,
      curator,
      akkaDicovery
    ),

    // workaround for akka discovery method lookup in dev-mode
    //  lagomDevSettings := Seq("akka.discovery.method" -> "lagom-dev-mode")
  )
  .dependsOn(`hello-proxy-api`, `hello-api`)


// This sample application doesn't need either Kafka or Cassandra so we disable them
// to make the devMode startup faster.
lagomCassandraEnabled in ThisBuild := false
lagomKafkaEnabled in ThisBuild := false

lagomServiceLocatorEnabled := false

// This adds an entry on the LagomDevMode Service Registry. With this information on
// the Service Registry a client using Service Discovery to Lookup("helloworld.GreeterService")
// will get "https://localhost:11000" and then be able to send a request.
// See declaration and usages of `hello-impl-HTTPS-port`.
lagomUnmanagedServices in ThisBuild := Map("helloworld.GreeterService" -> s"http://localhost:8000")

//----------------------------------


// Documentation for this project:
//    sbt "project docs" "~ paradox"
//    open docs/target/paradox/site/main/index.html
lazy val docs = (project in file("docs"))
  .enablePlugins(ParadoxPlugin)


//----------------------------------

ThisBuild / scalacOptions ++= List("-encoding", "utf8", "-deprecation", "-feature", "-unchecked")
