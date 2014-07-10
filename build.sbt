import AssemblyKeys._

scalaVersion := "2.10.4"

libraryDependencies += "net.sf.saxon" % "Saxon-HE" % "9.5.1-5"

mainClass in Compile := Some("edu.virginia.lib.bibframerator.Bibframerator")

assemblySettings

assemblyOption in assembly ~= { _.copy(prependShellScript = Some(defaultShellScript)) }

jarName in assembly := { s"${name.value}" }
