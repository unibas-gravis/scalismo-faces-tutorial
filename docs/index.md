# Scalismo-Faces tutorials

## _**Note:** This tutorial is under construction._

The following tutorials explain all the basic concepts behind Scalismo-Faces. Basic knowledge is required about the base library Scalismo (see [Scalismo tutorial](https://unibas-gravis.github.io/scalismo-tutorial/)).

### Preparation

To run the code in the tutorials, you will need to setup a Scala project,
which depends on the latest scalismo-faces version.

If you are new to Scala or have never worked with Scala in an IDE, 
follow the instructions in the guide
[Using Scalismo in an IDE](https://unibas-gravis.github.io/scalismo-tutorial/ide.html) to 
set up a project and programming environment.
To use Scalismo-Faces in an existing project, simply add the following lines to your ```build.sbt```.

```scala
resolvers += Resolver.bintrayRepo("unibas-gravis", "maven")

libraryDependencies ++=
  Seq("ch.unibas.cs.gravis" %% "scalismo-faces" % "0.10.1"),
  Seq("ch.unibas.cs.gravis" %% "scalismo-ui" % "0.13.0")
```

<!--- You will also need to [download](https://drive.switch.ch/index.php/s/zOJDpqh2ZGxzJJH) the datasets used in the tutorials and unzip them into your project folder. --->

### Tutorials

#### Basics - Images, Meshes, Model, Rendering & GUI-Blocks

The following tutorials guides you through our two most basic data structures when dealing with images and rendering: images and meshes. We call an image, as e.g. a photograph, a *pixel image*. The mesh structure is identical to the mesh used in Scalismo. However, we add color and work with normals extensively and thus introduce *values* defined *on* the mesh surface.

Further, interwoven you will also get to know the basic GUI functionality of Scalismo-Faces. It allows you to create simple windows, mainly for visualization but also with basic interaction, very easily.

* [Tutorial 1](tutorials/tutorial001.html): Images
* [Tutorial 2](tutorials/tutorial002.html): Meshes
* [Tutorial 3](tutorials/tutorial003.html): Face Model
* [Tutorial 4](tutorials/tutorial004.html): Rendering


### Other guides

- [Scalismo tutorial](https://unibas-gravis.github.io/scalismo-tutorial/)