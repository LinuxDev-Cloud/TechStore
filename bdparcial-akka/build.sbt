ThisBuild / scalaVersion := "2.13.12"

lazy val root = (project in file("."))
  .settings(
    name := "bdparcial-akka",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.6.21",
      "com.typesafe.akka" %% "akka-stream" % "2.6.21",
      "com.typesafe.akka" %% "akka-http"  % "10.2.10",
      "mysql" % "mysql-connector-java" % "8.0.33"
    )
  )
