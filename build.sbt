import de.heikoseeberger.sbtheader.HeaderPattern
import sbt.Keys._
import sbt._

lazy val commonSettings = Seq(
  organization := "it.vinmar",
  version := "0.2.0-SNAPSHOT",
  scalaVersion := "2.11.7",
  logLevel := Level.Info,
  crossScalaVersions := Seq("2.10.4", scalaVersion.toString),
  scalacOptions ++= Seq("-unchecked", "-deprecation"),
  resolvers ++= Seq("snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
    "releases" at "http://oss.sonatype.org/content/repositories/releases",
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    "Apache Development Snapshot Repository" at "https://repository.apache.org/content/repositories/snapshots/"
  )
)

lazy val root = (project in file(".")).
  configs(IntegrationTest).
  settings(commonSettings: _*).
  settings(Defaults.itSettings: _*).
  //enablePlugins(AutomateHeaderPlugin).
  settings(
    name := "regression-fe-scala"
)

val seleniumVersion = "2.53.0"
val akkaVersion = "2.3.6"
val poiVersion = "3.11"

val log4jDependencies = Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "org.slf4j" % "slf4j-ext" % "1.7.13"
)

val poiDependencies = Seq(
  "org.apache.poi" % "poi" % poiVersion,
  "org.apache.poi" % "poi-ooxml" % poiVersion
)

val akkaDependencies = Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "it,test"
)

val testingDependencies = Seq(
  "org.scalatest" %% "scalatest" % "2.2.4" % "it,test",
  "org.pegdown" % "pegdown" % "1.4.2" % "it,test"
)

libraryDependencies ++= Seq(
  "org.seleniumhq.selenium" % "selenium-server" % seleniumVersion,
  "org.eclipse.jetty" % "jetty-http" % "9.3.5.v20151012",
  "com.google.code.findbugs" % "jsr305" % "3.0.0",
  "com.github.scopt" %% "scopt" % "3.3.0"
) ++ log4jDependencies ++ poiDependencies ++ akkaDependencies ++ testingDependencies

fork in Test := true
fork in IntegrationTest := true

javaOptions in Test := Seq("-Dwebdriver.chrome.driver=./src/test/resources/chromedriver")

testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")
testOptions in IntegrationTest += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")

testOptions in IntegrationTest += Tests.Setup( () => println("Setup Integration Test") )
testOptions in IntegrationTest += Tests.Cleanup( () => println("Cleanup Integration Test") )

ScoverageSbtPlugin.ScoverageKeys.coverageMinimum := 50

ScoverageSbtPlugin.ScoverageKeys.coverageFailOnMinimum := true

//assemblyJarName in assembly := "something.jar"
test in assembly := {}
mainClass in assembly := Some("it.vinmar.Main")

// META-INF discarding
assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case x => MergeStrategy.first
}

lazy val headerForCode =
"""| /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
  | *   Copyright (C) 2016 Vincenzo Marrazzo                                   *
  | *                                                                         *
  | *   This program is free software; you can redistribute it and/or modify  *
  | *   it under the terms of the GNU General Public License as published by  *
  | *   the Free Software Foundation; either version 3 of the License, or     *
  | *   (at your option) any later version.                                   *
  | *                                                                         *
  | *   This program is distributed in the hope that it will be useful,       *
  | *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
  | *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
  | *   GNU General Public License for more details.                          *
  | *                                                                         *
  | *   You should have received a copy of the GNU General Public License     *
  | *   along with this program; if not, write to the                         *
  | *   Free Software Foundation, Inc.,                                       *
  | *   51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.          *
  | * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
  |
"""

headers := Map(
  "scala" -> ( HeaderPattern.cStyleBlockComment, headerForCode.stripMargin ),
  "java" -> ( HeaderPattern.cStyleBlockComment, headerForCode.stripMargin )
)