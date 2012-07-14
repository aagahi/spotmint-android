import sbt._

import Keys._
import AndroidKeys._
import scala._

object General {

  val resolutionRepos = Seq(
    "Local Maven Repository"  at "file:///Users/alag/.m2/repository/",
    ScalaToolsReleases,
    DefaultMavenRepository,
    ScalaToolsSnapshots
  )

  val settings = Defaults.defaultSettings ++ Seq (
    name := "SpotMint",
    version := "1.0.3",
    versionCode := 4,
    scalaVersion := "2.8.2",
    scalacOptions := Seq("-deprecation", "-encoding", "utf8"),
    platformName in Android := "android-7",
    resolvers ++= resolutionRepos
  )

  val proguardSettings = Seq (
    useProguard in Android := true
  )

  val proguardOptim = Seq(
    proguardOptimizations in Android := Seq( "-dontoptimize", "-verbose", "-keep class scala.*", "-keep class ws.nexus.*", "-keep class com.spotmint.*")
  )

  lazy val fullAndroidSettings =
    General.settings ++
    AndroidProject.androidSettings ++
    TypedResources.settings ++
    proguardSettings ++
    proguardOptim ++
    AndroidManifestGenerator.settings ++
    AndroidMarketPublish.settings ++ Seq (
      keyalias in Android := "spotmint",
      libraryDependencies ++= Seq(
        "ws.nexus"                    %% "websocket-client"   % "0.3.1",
        "com.google.android.maps"     % "maps"                % "7_r1"          % "provided"
  )
    )
}

object AndroidBuild extends Build {
  lazy val main = Project (
    "SpotMint",
    file("."),
    settings = General.fullAndroidSettings
  )

}
