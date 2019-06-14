## Meshes

The basic data structure for surface representation is called a *mesh*. You hopefully already encountered meshes in scalismo. A mesh represents a surface by *triangulation*. The mesh surface is a collection of triangles and their support points (called a *vertex*).

<!--- In our software a point is represented with the class `Point`, a triangle with the class `TriangleCell`. Further we provide a class `TriangleList` to hold all triangles and the `TriangleMesh` class to represent a mesh. ---->

```scala
import scalismo.common._
import scalismo.geometry._
import scalismo.mesh._

val points = IndexedSeq(
  Point(-100.0, 100.0, 0.0),
  Point(-95.0, -5.0, 0.0),
  Point(85.0, 0.0, 10.0),
  Point(90.0, 110.0, -10.0)
)

val triangles = IndexedSeq(
  TriangleCell(PointId(0), PointId(1), PointId(2)),
  TriangleCell(PointId(2), PointId(3), PointId(0))
)

val triangulation = TriangleList(triangles)
val mesh = TriangleMesh3D(points, triangulation)
```

We use the convention that a triangle is counter-clockwise oriented. If you get that wrong the normals of the mesh might point inwards instead of outwards and some visibility tests might fail.

In this tutorial, we will gradually develop what is required to generate a rendering of a mesh and will thus create the images ourselves. For the beginning, you can use our pre-fabricated mesh rendering function (You will understand every part of it at the end of this block).

```scala
import scalismo.faces.gui._
import scalismo.faces.gui.GUIBlock._
import scalismo.color._
import scalismo.faces.parameters._
import scalismo.faces.mesh._

val simpleImage = ParametricRenderer.renderParameterMesh(
  RenderParameter.defaultSquare,
  ColorNormalMesh3D(
    mesh,
    TriangleProperty(mesh.triangulation, IndexedSeq(
      RGBA(1,0,0),
      RGBA(0,1,0)
    )),
    mesh.cellNormals
  )
)

ImagePanel(simpleImage).displayIn("Mesh")
```

The mesh is very simple, it only contains two triangles.
It is much more interesting to study a
real face mesh with many triangles and points.

```scala
import java.io.File
import scalismo.faces.io.MeshIO

// as always, IO is guarded by Try, we only want to read the raw mesh (".shape")
val face = MeshIO.read(new File("data/rough_face.ply")).get.shape

val faceImage = ParametricRenderer.renderParameterMesh(
  RenderParameter.defaultSquare,
  ColorNormalMesh3D(face, ConstantProperty(face.triangulation, RGBA(0.9)), face.cellNormals)
)

ImagePanel(faceImage).displayIn("Mesh")
```

The mesh supports geometric operations, such as transformations.

```scala
import scalismo.faces.render._

val rotatedFace = face.transform(Rotation3D(math.Pi/4.0, EuclideanVector3D.unitY).apply)
val rotFaceImage = ParametricRenderer.renderParameterMesh(
  RenderParameter.defaultSquare,
  ColorNormalMesh3D(rotatedFace, ConstantProperty(rotatedFace.triangulation, RGBA(0.9)), rotatedFace.cellNormals)
)
ImagePanel(rotFaceImage).displayIn("Mesh")
```

A mesh defines a surface *parametrization* through its triangulation. Every point on the surface is characterized by the *triangle* it lies in and *barycentric coordinates* within the triangle. The triangle is identified through its `TriangledId` which is its index in the triangulation list.

A parametrized surface allows us to define values for *every point* on the surface, continuously! In this view, we regard the mesh as the *domain* of a function returning a value everywhere on the surface. Such a function is called `MeshSurfaceProperty` as it characterizes a *property* defined on the meshed surface. Properties are typically characteristics such as color and normals. The trait `MeshSurfaceProperty[A]` mainly defines the method `onSurface(triangleId, barycentricCoordinates)` which implements said function. It can be of arbitrary type `A`.

The most basic property available for every mesh is the continuous representation of the surface itself. For every `TriangleId` and `BarycentricCoordinate`, it returns the position in space at the specified point on the surface.

```scala
val noseTip = face.position.onSurface(TriangleId(2), BarycentricCoordinates(0.0, 1.0, 0.0))
```

Mesh normals are also available from scratch, both vertex and cell normals.

```scala
val noseNormal = face.vertexNormals.onSurface(TriangleId(2), BarycentricCoordinates(0.0, 1.0, 0.0))
println(noseNormal)
```

There are many different implementations of a `MeshSurfaceProperty`. They are structured according to "how" the data is available. The most common properties are:

- `ConstantProperty`: A single constant value everywhere.
- `SurfacePointProperty`: Defined (sampled) points at every *vertex*, uses barycentric interpolation within triangles.
- `TriangleProperty`: Defined for each triangle, constant value everywhere within triangle.
- `TextureMappedProperty`: Property values are defined on an 2d *image* combined with a mapping between surface and image plane.

For most of these property types, the companion object offers methods to construct / sample from other property types. This is, for example, very useful to average cell normals at each vertex to get vertex-based normals:

```scala
val vertexNormals = SurfacePointProperty.averagedPointProperty(face.cellNormals)
```

There are specific types for a few very important choices of surface properties

```scala
import scalismo.utils._
val rnd = Random(98)

// VertexColorMesh: "per-vertex" color on a mesh / SurfacePointProperty for color
// randomly color a mesh: random color for each vertex
val randomColors = IndexedSeq.fill(face.pointSet.numberOfPoints)(
  RGBA(rnd.scalaRandom.nextDouble(), rnd.scalaRandom.nextDouble(), rnd.scalaRandom.nextDouble())
)
val vcMesh = VertexColorMesh3D(
  face,
  SurfacePointProperty(face.triangulation, randomColors)
)

// ColorNormalMesh: mesh with normals and color, both can be arbitrary surface properties
// white face mesh with per-vertex normals
val colNormMesh = ColorNormalMesh3D(
  face,
  ConstantProperty(face.triangulation, RGBA.White),
  face.vertexNormals
)

// Display both meshes
val randomFaceImg = ParametricRenderer.renderParameterMesh(
  RenderParameter.defaultSquare,
  ColorNormalMesh3D(vcMesh)
)
val whiteFace = ParametricRenderer.renderParameterMesh(
  RenderParameter.defaultSquare,
  colNormMesh
)

shelf(
  ImagePanel(randomFaceImg),
  ImagePanel(whiteFace)
).displayIn("Mesh")
```

