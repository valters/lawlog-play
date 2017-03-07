name := """lawlog-play"""

version := "1.1-SNAPSHOT"

scalaVersion := "2.11.8"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies += cache
libraryDependencies += ws
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test

// bootstrap
libraryDependencies += "org.webjars" % "jquery" % "2.2.4"
libraryDependencies += "org.webjars" % "bootstrap" % "3.3.7-1" exclude("org.webjars", "jquery")
libraryDependencies += "org.webjars" % "font-awesome" % "4.7.0"

libraryDependencies += "org.webjars" % "vue" % "2.1.3"

// xml
libraryDependencies += "io.github.valters" % "valters-xml" % "1.0.0"

// acme
libraryDependencies += "io.github.valters" %% "play-acme-protocol" % "0.1.0-SNAPSHOT"

// logging
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.10"

fork in run := false
