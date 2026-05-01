ThisBuild / scalaVersion := "3.3.3"
ThisBuild / organization := "demo.slay"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .settings(
    name := "slay-demo-backend",
    Compile / mainClass := Some("slaydemo.backend.BackendApp"),
    libraryDependencies += "org.postgresql" % "postgresql" % "42.7.4"
  )
