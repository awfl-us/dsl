// build.sbt
ThisBuild / scalaVersion := "3.3.1"

name := "dsl"
organization := "us.awfl"
version := "0.1.0-SNAPSHOT"

// Circe Core + Generic + Parser + YAML
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core"   % "0.14.7",
  "io.circe" %% "circe-generic"% "0.14.7"
)

publishMavenStyle := true
scalacOptions ++= Seq("-deprecation", "-feature", "-language:implicitConversions")
