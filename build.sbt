name := "stack" // insert clever name here

// scalaVersion := "2.13.3"

// crossScalaVersions := Seq("2.10.3", "2.11.4")

scalaVersion := "2.13.8"

crossScalaVersions := Seq("2.12.10")

scalacOptions ++= Seq("-Xfatal-warnings", "-deprecation")

version := "1.0.1"

organization := "org.jliszka"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.0"
libraryDependencies += "org.scala-lang.modules" % "scala-jline" % "2.12.1"

initialCommands := """
                |import org.jliszka.stack._
                |
                """.stripMargin('|')
