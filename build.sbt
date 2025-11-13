// build.sbt
ThisBuild / scalaVersion := "3.3.1"

name := "dsl"
organization := "us.awfl"

// sbt-dynver: derive version from Git tags (e.g., v0.1.0). CI publishes only on release tags.
ThisBuild / versionScheme := Some("early-semver")
// Default tag prefix is "v"; keep it to match our release tags
// ThisBuild / dynverTagPrefix := "v"

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

ThisBuild / version ~= { v =>
  if (sys.env.get("CI").contains("true")) v
  else "0.1.0-SNAPSHOT"
}

// Dependencies
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core"    % "0.14.7",
  "io.circe" %% "circe-generic" % "0.14.7"
)

publishMavenStyle := true
scalacOptions ++= Seq("-deprecation", "-feature", "-language:implicitConversions")
