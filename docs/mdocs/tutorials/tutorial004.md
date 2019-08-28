# Model Rendering

```scala mdoc:silent
import scalismo.color._
import scalismo.common._
import scalismo.geometry._
import scalismo.mesh._
import scalismo.utils.Random
import scalismo.faces.image._
import scalismo.faces.parameters._
import scalismo.faces.render._
import scalismo.faces.mesh._
import scalismo.faces.momo._
import scalismo.faces.io._
import scalismo.faces.gui._
import scalismo.faces.sampling.face.MoMoRenderer
import breeze.linalg.DenseVector
import GUIBlock._

import java.io.File

scalismo.initialize()
```


In this tutorial block, you will learn about the structure of our renderer and the scene parametrization we use for fitting the model to images.

Rendering an image consists of two main parts. There is the *geometric* transformation which maps 3D positions and structures onto the image plane and there is the *shading* process which finds color values to display in the image. The result of the geometric step is correspondence between image pixel positions and surface points. In the Scalismo framework, we use a rasterizer to find such correspondences.

The general renderer of the framework (called `TriangleRenderer`) can be configured by the user in these two crucial steps. There is a trait `PointShader` which defines the geometric transformation between 3D space and image plane and there is the `PixelShader` which calculates the image value based on a given surface correspondence. The renderer only requires you to specify these two parts together with a `RenderBuffer`, a structure that builds your image.

## Render Parameters

In order to get a comparable and understandable scene setup, we use a pre-defined parametrization. All parameters describing a 3D scene of a single face are collected in a `RenderParameter`. The parametrization offers a tractable way of dealing with the geometric transformation part and also for shading using standard illumination models.

```scala mdoc:silent
// load the mesh to render
val faceMesh = MeshIO.read(new File("data/rough_face.ply")).get.shape

// generate a simple scene with a square image format
val scene = RenderParameter.defaultSquare

// the geometric transformation described by its parameters is directly available as a `PointShader`
val pointShader: PointShader = scene.pointShader
// or as a simple transformation of points.
// The 3rd dimension in the image plane is a depth value (required for hidden surface removal)
val renderTransform: (Point[_3D]) => Point[_3D] = scene.renderTransform

// the shading part can be generated for a given mesh. It is actually depending on the
// geometry of the mesh and also its colors
val pixelShader = scene.pixelShader(ColorNormalMesh3D(faceMesh, ConstantProperty(faceMesh.triangulation, RGBA.White), faceMesh.cellNormals))

// We can now render the face mesh directly using the triangle renderer
// Create an buffer of the right size using the parameters
val buffer = scene.imageSize.zBuffer(RGBA.WhiteTransparent)
TriangleRenderer.renderMesh(faceMesh, pointShader, pixelShader, buffer)
val image = buffer.toImage

ImagePanel(image).displayIn("Rendered Mesh")
```

A `RenderParameter` describes a face model instance together with the image generation process. It consists of multiple parts:

- `pose`: *model transform*, alignment of face in world
- `view`: *view transform*, alignment of camera in world
- `camera`: *projection* onto image plane, pinhole camera
- `imageSize`: viewport, scaling to actual image size
- `momo`: face description using the Morphable Model
- `directionalLight`, `environmentMap`: illumination description
- `colorTransform`: linear color transform of the final image

The class `RenderParameter` provides a default scene for our face model instances.

```scala mdoc:silent
// square, 512x512
val init = RenderParameter.defaultSquare
```

`RenderParameter` supports serialization to files:

```scala mdoc:silent
import scalismo.faces.io._

val paramFile = new File("init-face.rps")

RenderParameterIO.write(init, paramFile).get
val paramIn = RenderParameterIO.read(paramFile).get

println("parameter file is identical to standard scene: " + (paramIn == init))
```

The format is JSON and is thus also easily handled by the language of your choice. We use [spray-json](https://github.com/spray/spray-json), therefore you can access the serialized JSON string directly (or parse from strings).

```scala mdoc:silent
import spray.json._
import scalismo.faces.io.renderparameters.RenderParameterJSONFormat._

println(init.toJson.prettyPrint)
```


## Face

The face description part of the render parameter refers to the model parameters expansion of the Morphable Model (see [Face Model](04_ComputerGraphics_03_FaceModel.html)). It is of type `MoMoInstance` which is almost identical to `MoMoCoefficients` but uses `IndexedSeq[Double]` for coefficient values and a URI to identify the model. Let us construct the parameter part describing the coefficients-based face from the last section.

```scala mdoc:silent
// load the BFM
val modelURI = new File("data/model2017-1_face12_nomouth.h5").toURI
val model = MoMoIO.read(modelURI).get.neutralModel
val modelRenderer = MoMoRenderer(model, RGBA.WhiteTransparent).cached(5)
```

The parametric face description requires only coefficients and the model. A concrete mesh instance is not necessary.

```scala mdoc:silent
// the model is parametric: low-rank expansion of a Gaussian Process (~ PCA model)
// Example: A face with 3 std deviations on first shape and -3 on first color principal component
val coeffs = MoMoCoefficients(
  IndexedSeq(3.0), // shape
  IndexedSeq(-3.0) // color
)

// a MoMoInstance generates the corresponding model parameters
val face = model.instance(coeffs)

// we can also draw a random coefficient set
implicit val rnd = Random(1899)

val randomCoeffs = MoMoCoefficients(
  model.shape.coefficientsDistribution.sample(),
  model.color.coefficientsDistribution.sample(),
  DenseVector.zeros[Double](0)
)

// observe the random coefficients in the console
println("5 random shape coefficients:")
println(randomCoeffs.shape.toArray.take(5).mkString(", "))
println("5 random color coefficients:")
println(randomCoeffs.color.toArray.take(5).mkString(", "))
```

The parametric description requires a `MoMoInstance` rather than naked `MoMoCoefficients`.

```scala mdoc:silent
// the coefficients (+3/-3) face
val coeffInstance = MoMoInstance.fromCoefficients(coeffs, modelURI)

// note that above construction will result in a face description using only a single coefficient per shape and color
// coefficients are padded with 0
val fullInstance = coeffInstance.withNumberOfCoefficients(
  model.shape.rank, model.color.rank,  0
)

println("using " + fullInstance.shape.length + " shape coefficients")
println("using " + fullInstance.color.length + " color coefficients")
```

We now place the (+3/-3) face in our default scene. A `RenderParameter` is by default immutable. Replacing a part of it always generates a new instance. Exchanging individual parts of is conveniently available using `with*` methods:

```scala mdoc:silent
val initWithFace = init.withMoMo(fullInstance)
```

We can now visualize the faces using the `MoMoRenderer`, which will be explained later.

```scala mdoc:silent
// You will understand the details soon
// note that this requires the modelURI to identify the model
def renderFace(face: MoMoInstance): PixelImage[RGBA] = {
  modelRenderer.renderImage(init.withMoMo(face))
}

shelf(
  stack(
    // mean face of the model (all 0 coefficients)
    ImagePanel(renderFace(MoMoInstance.zero(model, modelURI))),
      label("mean")),
    stack(
      ImagePanel(renderFace(
        MoMoInstance.fromCoefficients(randomCoeffs, modelURI)
      )),
      label("random")),
    stack(
      ImagePanel(renderFace(coeffInstance)),
      label("coefficients (+3; -3)"))
  ).displayIn("Faces")
```


## Geometry: Pose and Camera

Now, we have the face instance ready. To render it into the image, we have to specify the geometric and the shading part of the renderer. Let us start with the geometric parts. The transformation from face instance coordinates to image pixel coordinates involves multiple steps:

- Model transform, `pose`: pose of the face (placement in the world) - typically rigid, scaling available
- View transform, `view`: pose of the camera taking the picture - rigid
- Projection, `camera`: perspective projection step to get 2D coordinates - pinhole camera
- Viewport transform, `imageSize`: scaling of image on normalized plane to actual image size / pixel grid

Each part is described by its own parameters. All of them influence the generated image. However, in a typical fitting application, we only adapt the *pose* and the focal length of the pinhole *camera*.

```scala mdoc:silent
// We define a simple display utility method: It renders and displays a parametrized instance
// for reference, we always render the initial default setup on the left-hand side
def displayParameters(params: RenderParameter, reference: RenderParameter, i: Int): Unit = {
  shelf(
    stack(
      ImagePanel(modelRenderer.renderImage(reference)),
      label("reference")
    ),
    stack(
      ImagePanel(modelRenderer.renderImage(params)),
      label("parameter rendering")
    )
  ).displayIn(s"Rendering ${i}")
}
```

The pose of the face places it within the world's coordinate system. It consists of a rigid part with rotation and translation and also allows for isotropic scaling. The parametrization uses 3 angles for yaw (y-axis), pitch (x-axis) and roll (z-axis), a Vector3D for translation and a factor for scaling. In the default scene, the face is 1 meter away from the camera. The default `view` is such that the camera is placed at the (world's) origin and faces the negative z-direction. The upwards axis is aligned with the positive y-direction. Units of length are measured in mm while angles have radian values.
(Image camera setup)

```scala mdoc:silent
val Pose(scaling, translation, roll, yaw, pitch) = initWithFace.pose
println("default pose:")
println(s"scaling=$scaling")
println(s"(yaw, roll, pitch) = ($yaw, $roll, $pitch)")
println(s"translation=$translation") // -1000 mm shift with respect to the origin

// The complete geometric transform is available as `.renderTransform`
// The result (x, y, z) is (x, y): Pixel coordinates, 3rd dimension is a depth value
val geometricTransform: (Point[_3D]) => Point[_3D] = initWithFace.renderTransform

// find the position of the tip of the nose in the 512x512 pixel image
val noseLocation = geometricTransform(face.shape.position.atPoint(PointId(8192)))
println("nose is at: " + noseLocation)
```

We observe the effects of changing some of the parameters of the pose transform:

```scala mdoc:silent
val initPose = initWithFace.pose

// yaw (let the face look sideways, 45 deg)
val sideView45 = initWithFace.withPose(initPose.withYaw(math.Pi/4.0))
displayParameters(sideView45, initWithFace, 0)

// translation (move back, 4m away from the camera)
val backMover = initWithFace.withPose(initPose.withTranslation(EuclideanVector(0.0, 0.0, -4000)))
displayParameters(backMover, initWithFace, 1)
```

Note that the face actually occludes *itself* when rotated to the side! The effect is called self-occlusion and appears as soon as a complex object is projected from 3D to 2D. It is very relevant for fitting applications as it prevents a 1:1 correspondence between pixels and the surface. There are always parts you cannot see from the current view. But which parts you can see depends on pose parameters.

The pose affects an image drastically in terms of the pixels a face actually covers. The `view` transform has very similar effects but acts in reverse as it moves/rotates the camera taking the picture rather than the face.

The pinhole camera model only uses few actual parameters. We mainly need the focal length, especially in fitting applications. With sensor-size, you can adapt the camera to match different existing photography systems.

```scala mdoc:silent
val initCamera = initWithFace.camera
val Camera(focalLength, pp, sensorSize, near, far, orthographic) = initWithFace.camera
println("default camera:")
println("focal length: " + focalLength)
println("sensor size: " + sensorSize)
println("principal point: " + pp)
println(s"(near, far): ($near, $far)")
println("orthographic: " + orthographic)

// the focal length affects the scaling of the image ("zoom")
val zoomed = initWithFace.withCamera(initCamera.copy(focalLength = 15))
displayParameters(zoomed, initWithFace, 2)
```

A perspective camera model leads to a non-linear distortion of the image depending on the camera distance. There is a division by the distance involved in the calculation of the image position of a point. The closer the object is to the camera, the stronger the effect is. However, moving an object farther away not only attenuates the perspective effect, it also shrinks the image. To study the effect, we thus *compensate* for the size change by adapting the focal length to ensure an equal apparent size of the face in the image.

```scala mdoc:silent
// close to camera: 50cm
val closeToCam = initWithFace.
  withCamera(initCamera.copy(focalLength = initCamera.focalLength / 2.0)).
  withPose(initPose.withTranslation(EuclideanVector(0.0, 0.0, initPose.translation.z/2.0)))
println("camera distance: " + -closeToCam.pose.translation.z)

// far away: 4m
val farAway = initWithFace.
  withCamera(initCamera.copy(focalLength = initCamera.focalLength * 4.0)).
  withPose(initPose.withTranslation(EuclideanVector(0.0, 0.0, initPose.translation.z * 4.0)))
println("camera distance: " + -farAway.pose.translation.z)

// default distance of 1m
shelf(
  ImagePanel(modelRenderer.renderImage(closeToCam)),
  ImagePanel(modelRenderer.renderImage(farAway))
).displayIn("Perspective Effect")
```

The effects of camera distance and focal length are very similar but only the camera distance also affects the distortion.

The last step of the geometric rendering part is a viewport transformation. The normalized image plane coordinates need to be mapped into the actual pixel grid. Here, only the image size matters. However, you have to be careful to match the aspect ratio of the camera sensor and the image - otherwise you get "non-quadratic" pixels. The pixel size of the image is the number of samples your sensor takes (typically the "Mega pixels").

```scala mdoc:silent
// Our default camera uses a quadratic sensor and thus generates quadratic images
println("sensor aspect ratio: " + initCamera.sensorSize.x / initCamera.sensorSize.y)
// you can generate higher or lower resolution images of the same scene
val lowRes = initWithFace.withImageSize(ImageSize(128, 128))
displayParameters(lowRes, initWithFace, 4)

// be careful to respect the aspect ratio
val wrongAspect = initWithFace.withImageSize(ImageSize(256, 128))
displayParameters(wrongAspect, initWithFace, 5)

// To change the image size but keep the rendered object at constant size:
// (will add background around or create cropped images)
val croppedView = initWithFace.forImageSize(256, 128)
displayParameters(croppedView, initWithFace, 6)

// You often need to adapt to an image size with a different aspect ratio
// `fitToImageSize` adapts the aspect ratio of the rendering to match the new image size
// without distorting the face. It scales down the face if required.
val broadView = initWithFace.fitToImageSize(512, 768)
println("broad aspect ratio: " + broadView.imageSize.aspectRatio)
println("broad sensor aspect: " + broadView.camera.sensorSize.x / broadView.camera.sensorSize.y)
displayParameters(broadView, initWithFace, 7)

// you can adapt the camera to e.g. 35mm film with 100mm focal length (which might be more
// familiar to you)
val cam100mm = initWithFace.withCamera(Camera.for35mmFilm(100.0))
println("35mm sensor size: " + cam100mm.camera.sensorSize)
```

## Rasterization: Correspondence from Image to Surface

The geometric rendering part maps every 3D coordinate to its corresponding 2D image pixel coordinates. However, the reverse transformation, finding a surface point for each image pixel, is required to paint an image. In most rendering engines, this is achieved by rasterization. The process determines the matching surface point by identifying pixels and surface points by their `TriangledId` and `BarycentricCoordinates` within that triangle. The process is typically triangle-oriented, performed for each triangle of the object. The result can be accessed easily:

```scala mdoc:silent
import TriangleRenderer.TriangleFragment

// create buffer holding all correspondence information in the form of a `TriangleFragment`
val surfaceImageCorr = ZBuffer[Option[TriangleFragment]](initWithFace.imageSize.width, initWithFace.imageSize.height, None)
// "render" the correspondence information, all geometric transformation information is
// captured in the `PointShader`
TriangleRenderer.renderCorrespondence(face.shape, initWithFace.pointShader, surfaceImageCorr)

// check for the pixel (255, 255)
surfaceImageCorr.toImage(255, 255) match {
  case Some(TriangleFragment(mesh, triangleId, worldBCC, x, y, z, ccwWinding)) =>
    println(s"Pixel at ($x, $y) is from ")
    println("Triangle: " + triangleId.id)
    println("at coordinates: " + worldBCC)
    println("The triangle has counter-clockwise winding: " + ccwWinding)
  case None =>
    println("No visible surface point at (255, 255)")
}
```

## Shading: Light and Surface Interaction

The correspondence information can be used to draw the image: Just *paint* the pixel using the color of the surface at the specified position!

```scala mdoc:silent
val selfRendering = surfaceImageCorr.toImage.map{
  case Some(TriangleFragment(_, triangleId, worldBCC, _, _, _, _)) =>
    // get color at specified surface point
    face.color.onSurface(triangleId, worldBCC)
  case None => RGBA.BlackTransparent // no visible surface, transparent color
}

displayParameters(broadView, initWithFace, 77)

ImagePanel(selfRendering).displayIn("Hand-crafted Shading")
```

You might have noticed a color difference to the earlier renderings. This is due to the simple nature of our *shading*. We do not consider light interaction but directly use the "raw" face color. Thus, we miss effects of surface-light interaction completely. Such effects are crucial to produce more realistic results.

Shading is the second part of rendering. Once image-to-surface correspondence has been established, we need to calculate the actually observed color values resulting from matter-light interaction. Shading methods typically implement specific *surface reflectance* models which describe how the surface interacts with light.

In our framework, we mainly use an efficient empirical model using Spherical Harmonics expansion. However, there is also the possibility to use Phong reflectance or more advanced reflectance models (such as Torrence-Sparrow and Oren-Nayar). The Spherical Harmonics (SH) illumination encodes a whole environment map. It captures the light irradiance from every possible direction. The reflectance model behind the SH expansion is typically *Lambertian*.

```scala mdoc:silent
// We can create a simple light situation with directional light and some ambient (constant) contributions:
val lightDirection = EuclideanVector3D.unitZ // illumination directly from the camera direction
val shIllum = SphericalHarmonicsLight.fromAmbientDiffuse(RGB(0.4), RGB(0.6), lightDirection)
val faceIllum = initWithFace.withEnvironmentMap(shIllum)
```

To render an image, we need to create a `PixelShader`. This is the piece of code which calculates the color given a surface correspondence. You wrote this code yourself a few lines above. Shading calculations heavily depend on the *normals* of the surface, therefore we need to supply a colored mesh with normals. `PointShader`, `PixelShader` and a buffer are all we need to render an image:

```scala mdoc:silent
val shShader = faceIllum.pixelShader(face)
val shRendering = TriangleRenderer.renderMesh(
  face.shape,
  faceIllum.pointShader,
  shShader,
  faceIllum.imageSize.zBuffer(RGBA.WhiteTransparent)
).toImage
```

The result looks very different from our earlier hand-crafted approach:

```scala mdoc:silent
shelf(
  ImagePanel(selfRendering),
  ImagePanel(shRendering)
).displayIn("Rendering A")
```

The orientation of the surface with respect to the light source heavily influences its apparent brightness in the image. The front-facing nose tip is very bright while side-facing back parts of the cheek are considerably darker. We can intensify the difference by illuminating from the side.

```scala mdoc:silent
val rightIllum = initWithFace.withEnvironmentMap(
  SphericalHarmonicsLight.fromAmbientDiffuse(RGB(0.4), RGB(0.6), EuclideanVector(2.0, 0.0, 0.5).normalize)
)
displayParameters(rightIllum, initWithFace, 8)
```

Now, the right cheek lies in dark shadow while the left cheek is clearly illuminated. This kind of shadow is called *attached shadow*. It is not *cast* by a light-occluding object but the result of local normal direction pointing "away" from the light source. Such parts of the surface appear dark because they cannot really *absorb* light energy and thus also do not emit light (unless they are active sources of light).

The SH illumination model, as implemented, can express arbitrary light distribution but only local interaction. It cannot generate *cast shadows*. There is no global geometric integrator which checks whether light paths are actually occluded by other parts of the object. Such global illumination is extremely costly to calculate.

The model also lacks specular highlights. But those are available using a Phong reflectance model. First, you have to turn off the SH contribution or you get the rendering result with both contributions.

```scala mdoc:silent
//val specHighlight = initWithFace.noEnvironmentMap.withDirectionalLight(
//  DirectionalLight(RGB(0.4), RGB(0.6), EuclideanVector(1, 0, 1).normalize, RGB(0.12), 8.0)
//)
//displayParameters(specHighlight, initWithFace, 9)
```

Do not overdo it with the specular effects. It can quickly lead to a "glass" head appearance.

```scala mdoc:silent
displayParameters(initWithFace.noEnvironmentMap.withDirectionalLight(
  DirectionalLight(RGB(0.4), RGB(0.6), EuclideanVector(1, 0, 1).normalize, RGB(0.4), 100.0)
), initWithFace, 10)
```

## Parametric Rendering

You now know how to generate images using `PointShader`s and `PixelShader`s. This is very powerful but often not that convenient when you just want to render a specific parameter instance. For these cases, there is the `MoMoRenderer` which we use for fitting. It is designed to offer simple parametric rendering interfaces for landmarks, images and more.

```scala mdoc:silent
import scalismo.faces.sampling.face._

val momoRenderer = MoMoRenderer(model)

// it renders images
val modelImage = momoRenderer.renderImage(initWithFace)
ImagePanel(modelImage).displayIn("Rendering B")

// it renders landmarks positions
val noseTipLM = momoRenderer.renderLandmark("center.nose.tip", initWithFace)
println("Nose tip: " + noseTipLM)

// it can be cached to prevent recalculation of already rendered images
val cachedRenderer = momoRenderer.cached(5) // cache last 5 rendering calls
```

If you want more flexibility and use `RenderParameters` rather to describe
your scene setup but want to render your *own mesh*, not the model, then you can use the `ParametricRenderer`:

```scala mdoc:silent
val meshRendering = ParametricRenderer.renderParameterVertexColorMesh(init, face)
ImagePanel(meshRendering).displayIn("Rendering C")
```

## Summary

For rendering, you need two processes: a *geometric* transformation from 3D to the pixel grid and a color calculation, called *shading*. These processes are implemented by `PointShader` and `PixelShader` respectively. Using these code blocks, you can render meshes with `TriangleRenderer`.

A scene is parameterized by a `RenderParameter`. It describes the face, its pose and view, the camera setup and the illumination. The most simple way to obtain the parameters point and pixel shaders is most simple using `.pointShader` and `.pixelShader`.

Convenience renderers exist for parameterized scenes. There is `MoMoRenderer` to render model instances in a parameteric scene setup and there is a `ParametricRenderer` to render arbitrary meshes in a parametric scene setup.