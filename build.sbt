import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings

val AkkaVersion           = "2.6.18"
val AkkaHttpVersion       = "10.2.8"
val AkkaManagementVersion = "1.1.3"

val `akka-cluster-playground` = project
  .in(file("."))
  .settings(multiJvmSettings: _*)
  .settings(
    organization := "com.lightbend.akka.samples",
    version := "1.0",
    scalaVersion := "2.12.11",
    Compile / scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlog-reflective-calls",
      "-Xlint"
    ),
    Compile / javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    javaOptions ++= Seq(
      "-Xms128m",
      "-Xmx2048m",
      "-XX:+UseZGC",
      // if you are using JDK17 please enable below
      "-J--add-opens=java.base/java.util.concurrent=ALL_UNNAMED"
    ),
    libraryDependencies ++= Seq(
      // 1. your private dependencies

      // 2. Basic dependencies for a clustered application
      "com.typesafe.akka"             %% "akka-stream"                       % AkkaVersion,
      "com.typesafe.akka"             %% "akka-stream-typed"                 % AkkaVersion,
      "com.typesafe.akka"             %% "akka-serialization-jackson"        % AkkaVersion,
      "com.typesafe.akka"             %% "akka-cluster-typed"                % AkkaVersion,
      "com.typesafe.akka"             %% "akka-cluster-metrics"              % AkkaVersion,
      "com.typesafe.akka"             %% "akka-cluster-sharding-typed"       % AkkaVersion,
      "com.typesafe.akka"             %% "akka-actor-testkit-typed"          % AkkaVersion % Test,
      "com.typesafe.akka"             %% "akka-stream-testkit"               % AkkaVersion % Test,
      "com.typesafe.akka"             %% "akka-multi-node-testkit"           % AkkaVersion % Test,
      // 3. Akka Management powers Health Checks and Akka Cluster Bootstrapping
      "com.lightbend.akka.management" %% "akka-management"                   % AkkaManagementVersion,
      "com.typesafe.akka"             %% "akka-http"                         % AkkaHttpVersion,
      "com.typesafe.akka"             %% "akka-http2-support"                % AkkaHttpVersion,
      "com.typesafe.akka"             %% "akka-http-spray-json"              % AkkaHttpVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-http"      % AkkaManagementVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion,
      "com.lightbend.akka.discovery"  %% "akka-discovery-kubernetes-api"     % AkkaManagementVersion,
      "com.typesafe.akka"             %% "akka-discovery"                    % AkkaVersion,
      "ch.qos.logback"                 % "logback-classic"                   % "1.2.11",
      "org.scalatest"                 %% "scalatest"                         % "3.2.11"    % Test,
      // 4. Extra tools
      "net.codingwell"                %% "scala-guice"                       % "5.0.2",
      "com.colofabrix.scala"          %% "figlet4s-core"                     % "0.3.1",
      "io.kamon"                      %% "kamon-bundle"                      % "2.4.8",
      "io.kamon"                      %% "kamon-apm-reporter"                % "2.4.8"
    ),
    run / fork := true,
    Global / cancelable := false, // ctrl-c
    // disable parallel tests
    Test / parallelExecution := false,
    // show full stack traces and test case durations
    Test / testOptions += Tests.Argument("-oDF"),
    Test / logBuffered := false,
    licenses := Seq(
      ("CC0", url("http://creativecommons.org/publicdomain/zero/1.0"))
    )
  )
  .configs(MultiJvm)
