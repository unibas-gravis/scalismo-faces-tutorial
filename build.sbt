organization  := "ch.unibas.cs.gravis"

name := """scalismo-faces-tutorial"""
version       := "0.10.1"

scalaVersion  := "2.12.8"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += Resolver.bintrayRepo("unibas-gravis", "maven")

resolvers += Opts.resolver.sonatypeSnapshots

libraryDependencies  ++= Seq(
            "ch.unibas.cs.gravis" %% "scalismo-faces" % "0.10.1",
            "ch.unibas.cs.gravis" %% "scalismo-ui" % "0.13.0"
)

lazy val root = (project in file("."))

lazy val docs = project       // new documentation project
  .in(file("myproject-docs"))
  .settings(
    mdocIn := new java.io.File("docs/mdocs/"),
    mdocOut := new java.io.File("docs/")
  )
  .dependsOn(root)
  .enablePlugins(MdocPlugin)
 
