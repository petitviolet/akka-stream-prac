name := """akka-stream-prac"""

version := "1.0"

scalaVersion := "2.11.7"

val akkaVersion = "2.4.9-RC2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion
)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))

fork := true
connectInput in run := true

