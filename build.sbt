name := "razam"

version := "2.7.x"

scalaVersion := "2.13.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies += guice
libraryDependencies += jdbc
libraryDependencies += evolutions
libraryDependencies += ws

libraryDependencies += "org.postgresql" % "postgresql" % "42.2.6"

libraryDependencies += "org.playframework.anorm" %% "anorm" % "2.6.4"
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test

val JgitVersion = "5.5.0.201909110433-r"
libraryDependencies += "org.eclipse.jgit" % "org.eclipse.jgit.http.server" % JgitVersion
libraryDependencies += "org.eclipse.jgit" % "org.eclipse.jgit.archive" % JgitVersion

// TOOD: clear dependencies
libraryDependencies += "commons-io" % "commons-io" % "2.6"
libraryDependencies += "org.mindrot" % "jbcrypt" % "0.3m"
libraryDependencies += "org.apache.tika" % "tika-core" % "1.22"

libraryDependencies += "com.googlecode.juniversalchardet" % "juniversalchardet" % "1.0.3"


ThisBuild / scalacOptions ++= List("-encoding", "utf8", "-deprecation", "-feature", "-unchecked", "-Xfatal-warnings")
ThisBuild / javacOptions ++= List("-Xlint:unchecked", "-Xlint:deprecation", "-Werror")
