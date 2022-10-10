val scalaV = "2.13.9"
val scalaTestV = "3.2.13"

libraryDependencies ++= Seq(
  "net.java.dev.jna" % "jna" % "5.12.1",
  "org.scalatest" %% "scalatest" % scalaTestV % "test"
)

scalaVersion := scalaV

run / fork := true
