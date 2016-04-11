// Package description
maintainer := "scorex <scorex-dev@groups.io>"
packageSummary := "Permacoin implementation on top of Scorex framework"
packageDescription := "Package for permacoin testnet"

enablePlugins(JavaAppPackaging)

//Debian settings
enablePlugins(DebianPlugin)
linuxPackageMappings in Debian := linuxPackageMappings.value
name in Debian := name.value
version in Debian := version.value
genChanges in Debian := new File("changelog.md")

name in Universal := name.value

enablePlugins(JavaAppPackaging)

mappings in Universal <+= (packageBin in Compile) map { jar =>
  jar -> ("lib/" + jar.getName)
}

dockerExposedPorts := Seq(9084, 9085)

mappings in Universal += {
  file("settings.json") -> "settings.json"
}