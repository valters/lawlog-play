name := """lawlog-play"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.8"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies += cache
libraryDependencies += ws
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test

// bootstrap
libraryDependencies += "org.webjars" % "jquery" % "2.2.4"
libraryDependencies += "org.webjars" % "bootstrap" % "3.3.7-1" exclude("org.webjars", "jquery")
libraryDependencies += "org.webjars" % "font-awesome" % "4.7.0"

// xml
libraryDependencies += "io.github.valters" % "valters-xml" % "1.0.0"

// acme
libraryDependencies += "com.nimbusds" % "nimbus-jose-jwt" % "4.34.1"

// logging
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.10"

fork in run := false
