import com.typesafe.sbt.packager.docker.DockerChmodType

name := "hello-proxy-impl"
val aspectVersion = "1.8.10"
javaOptions in run += "-javaagent:" + System.getProperty("user.home") + "/.ivy2/cache/org.aspectj/aspectjweaver/jars/aspectjweaver-" + aspectVersion + ".jar"


maintainer := "Sajith Nandasena <sajith@zmessenger.com>"

version in Docker := "latest"

dockerRepository := Some("dmp_kit")

dockerChmodType := DockerChmodType.UserGroupWriteExecute


dockerExposedVolumes := Seq("/opt/docker/logs")

dockerBaseImage := "dmp_kit/java8:latest"

enablePlugins(JavaAppPackaging)