import de.heikoseeberger.sbtheader.HeaderPattern
import sbt.Keys._
import sbt._

lazy val commonSettings = Seq(
  version := "0.2.0-SNAPSHOT",
  logLevel := Level.Info,
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
    organization := "it.vinmar",
    scalaVersion := "2.11.7",
    name := "regression-fe-scala"
)

val seleniumVersion = "2.53.0"
val akkaVersion = "2.4.3"
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
  "org.scalatest" %% "scalatest" % "2.2.6" % "it,test",
  "org.pegdown" % "pegdown" % "1.4.2" % "it,test"
)

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "1.0.4",
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
javaOptions in IntegrationTest += "-Dintegration.test.grid=http://localhost:4444/wd/hub"

testOptions in IntegrationTest += Tests.Setup( () => println("Setup Integration Test") )
testOptions in IntegrationTest += Tests.Cleanup( () => println("Cleanup Integration Test") )

jacoco.settings
itJacoco.settings

//assemblyJarName in assembly := "something.jar"
test in assembly := {}
mainClass in assembly := Some("it.vinmar.selenium.Main")

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