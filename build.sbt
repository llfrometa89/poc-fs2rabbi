ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

lazy val root = (project in file("."))
  .settings(
    name := "poc-fs2rabbit"
  )

lazy val fs2RabbitVersion = "4.1.1" //https://mvnrepository.com/artifact/dev.profunktor/fs2-rabbit_3/4.1.1

libraryDependencies += "dev.profunktor" %% "fs2-rabbit" % fs2RabbitVersion
libraryDependencies += "dev.profunktor" %% "fs2-rabbit-circe" % fs2RabbitVersion
libraryDependencies += "org.typelevel" %% "cats-effect" % "3.3.5"


