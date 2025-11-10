// sbt plugins
// Publishing is managed by sbt-ci-release: https://github.com/sbt/sbt-ci-release
// It handles both snapshot (on branch) and release (on tag) publishing to Sonatype/Maven Central.
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.6.1")

// Derive version from Git tags/commits
addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.0.1")
