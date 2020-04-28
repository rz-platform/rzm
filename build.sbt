val JgitVersion = "5.7.0.202003110725-r"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    name := "razam",
    version := "2.8.x",
    maintainer := "eugenebosyakov@gmail.com",
    scalaVersion := "2.13.1",
    excludeFilter := "node_modules" || ".cache", // FE deps and Parcel cache
    libraryDependencies ++= Seq(
      guice,
      jdbc,
      evolutions,
      "org.postgresql"                   % "postgresql"                   % "42.2.6",
      "org.playframework.anorm"          %% "anorm"                       % "2.6.5",
      "org.scalatestplus.play"           %% "scalatestplus-play"          % "5.0.0" % Test,
      "org.eclipse.jgit"                 % "org.eclipse.jgit.http.server" % JgitVersion,
      "org.eclipse.jgit"                 % "org.eclipse.jgit.archive"     % JgitVersion,
      "commons-io"                       % "commons-io"                   % "2.6",
      "org.mindrot"                      % "jbcrypt"                      % "0.3m",
      "org.apache.tika"                  % "tika-core"                    % "1.22",
      "com.googlecode.juniversalchardet" % "juniversalchardet"            % "1.0.3",
    ),
    scalacOptions ++= List("-encoding", "utf8", "-deprecation", "-feature", "-unchecked", "-Xfatal-warnings"),
    javacOptions ++= List("-Xlint:unchecked", "-Xlint:deprecation", "-Werror")
  )
