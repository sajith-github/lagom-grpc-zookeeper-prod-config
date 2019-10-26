import com.typesafe.sbt.packager.docker.DockerChmodType

name := "hello-impl"


maintainer := "Sajith Nandasena <sajith@zmessenger.com>"

version in Docker := "latest"

dockerRepository := Some("dmp_kit")

dockerChmodType := DockerChmodType.UserGroupWriteExecute

dockerExposedVolumes := Seq("/opt/docker/logs")

dockerBaseImage := "dmp_kit/java8:latest"

enablePlugins(JavaAppPackaging)