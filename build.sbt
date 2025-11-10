// build.sbt
ThisBuild / scalaVersion := "3.3.1"

name := "dsl"
organization := "us.awfl"

// sbt-dynver: derive version from Git tags (e.g., v0.1.0) and commits for snapshots
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / dynverSonatypeSnapshots := true         // append -SNAPSHOT for non-tag builds
// Default tag prefix is "v"; keep it to match our CI trigger
// ThisBuild / dynverTagPrefix := "v"

// Pin Sonatype to s01 host to avoid redirect loops and ensure correct resolver selection
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / publishTo := sonatypePublishTo.value

// Project metadata required by Maven Central
ThisBuild / description := "Workflow DSL for Scala 3: typed values, CEL expressions, and declarative steps to build readable, testable pipelines."
ThisBuild / homepage := Some(url("https://github.com/awfl-us/dsl"))
ThisBuild / licenses := List("MIT" -> url("https://opensource.org/licenses/MIT"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/awfl-us/dsl"),
    "scm:git:https://github.com/awfl-us/dsl.git",
    Some("scm:git:ssh://git@github.com/awfl-us/dsl.git")
  )
)
ThisBuild / developers := List(
  Developer(id = "awfl", name = "AWFL", email = "opensource@awfl.us", url = url("https://awfl.us"))
)
ThisBuild / pomIncludeRepository := { _ => false }

// Dependencies
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core"    % "0.14.7",
  "io.circe" %% "circe-generic" % "0.14.7"
)

publishMavenStyle := true
scalacOptions ++= Seq("-deprecation", "-feature", "-language:implicitConversions")
