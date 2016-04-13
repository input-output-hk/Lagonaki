import com.typesafe.config.ConfigFactory

organization := "org.consensusresearch"

val appConf = ConfigFactory.parseFile(new File("src/main/resources/lagonaki.conf")).resolve().getConfig("app")

name := "lagonaki"

version := appConf.getString("version")

scalaVersion := "2.11.8"

resolvers += "SonaType" at "https://oss.sonatype.org/content/groups/public"

libraryDependencies ++= Seq(
  "org.consensusresearch" %% "scorex-basics" % "1.2.+",
  "org.consensusresearch" %% "scorex-consensus" % "1.2.+",
  "org.consensusresearch" %% "scorex-perma" % "1.2.+",
  "org.consensusresearch" %% "scorex-transaction" % "1.2.+"
)

//assembly settings
assemblyJarName in assembly := "lagonaki.jar"

test in assembly := {}

mainClass in assembly := Some("scorex.lagonaki.Application")