// sbt plugins
// Publishing is managed by sbt-ci-release: https://github.com/sbt/sbt-ci-release
// It handles publishing to Sonatype Central Portal with sbt 1.11.x and sbt-ci-release 1.11.x+.
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.0")

// Derive version from Git tags/commits
addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.0.1")
