# The Morphable Model of Faces

Gaussian Processes can be very successfully applied to *shape* modeling, as you now know. In order to deal with face images in an Analysis-by-Synthesis setting, we need to extend the idea to take into account with face *color*, too. The *Morphable Model* has originally been developed for faces and included a color model right from the start [Blanz and Vetter 1999]. Originally described as a *Principal Component Analysis (PCA) model*, it fits the Gaussian Process framework very well. Adding independent color is just adding another process to handle color values rather than geometric point locations.

We specify a face by its expansion coefficients within the face model. The model consists of two independent Gaussian Process models for *shape* and *color*. The models are used in a low-rank expansion with a statistical covariance function and a "spherical noise assumption" - called *Probabilistic Principal Components Analysis*. The trait `MoMo` handles this *Morphable Model*.

Let us set first some imports and initialize scalismo, as we need the hdf5 library to load a model.

```scala mdoc:silent
import java.io.File

import scalismo.mesh._
import scalismo.color._
import scalismo.common.UnstructuredPointsDomain
import scalismo.geometry._
import scalismo.utils.Random

import scalismo.faces.io._
import scalismo.faces.momo._
import scalismo.faces.mesh._
import scalismo.faces.image._
import scalismo.faces.parameters._

// model loading needs the native HDF5 library
scalismo.initialize()
// create a seeded random number generator which will be passed implicitly
implicit val rng = Random(1024L)
```

Next we load the model from an `URI`:
```scala mdoc:silent
//val modelURI = new File("data/model2017-1_face12_nomouth.h5").toURI
val modelURI = new File("data/model-bfm.h5").toURI
val model = MoMoIO.read(modelURI).get
```

To get a `VertexColorMesh3D` representing the mean of the model, we can
first get from the models coefficients with all zeros and then use those
coefficients to generate the instance.

```scala mdoc:silent
val meanCoeffs = model.zeroCoefficients
val meanFace: VertexColorMesh3D = model.instance(meanCoeffs)
```

The model represents the distribution of faces it was trained with. We
can draw a random sample from the distribution.

```scala mdoc:silent
val randomFace = model.sample()
```

As convenience, we are free to specify less coefficients than the model
has components. Remember that the model is a low-rank approximation of a
Gaussian process or learned from examples using PCA. The coefficients we
do not specify are treated as zeros. However we can not specify more
coefficicents than the models rank.

```scala mdoc:silent
val coeffs = MoMoCoefficients(
  IndexedSeq(3.0, 0.0, 0.0),  // shape
  IndexedSeq(-3.0, 0.0, 0.0), // color
  IndexedSeq(0.0)             // expression
)
// generate the model instance
val coeffFace = model.instance(coeffs)
```

A simple convenience function to render a model instance, that you will
understand later, can be defined as follows:

```scala mdoc:silent
def renderFace(face: VertexColorMesh3D): PixelImage[RGBA] = {
  ParametricRenderer.renderParameterVertexColorMesh(
    RenderParameter.defaultSquare,
    face)
}
```

Next we use the method to render an the images for the last three meshes
and display them with the help of the `GUIBlock`s.

```scala mdoc:silent
import scalismo.faces.gui._
import GUIBlock._

shelf(
  stack(
    ImagePanel(renderFace(meanFace)),
    label("mean")),
  stack(
    ImagePanel(renderFace(randomFace)),
    label("random")),
  stack(
    ImagePanel(renderFace(coeffFace)),
    label("coefficients (+3; -3)"))
).displayIn("Faces")
```

The underlying models of a Morphable Model are so-called `PancakeDLRGP` models. This refers to "Pancake Discrete Low-Rank Gaussian Process". The *pancake* is the extension of a *strict* low-rank model to a full rank model with a spherical Gaussian covariance. It extends the regular, *strict* low-rank model which is only properly defined within its low-rank subspace to the *full* instance space. This is achieved by adding some minor uncorrelated Gaussian "noise" at every point. The model is mostly known as *Probabilistic PCA* (or *Spherical PCA*) when applied to a PCA low-rank model. However, it can be applied to any low-rank GP model and generates a GP with the kernel

$$
k(x, y) = k_{\text{LowRank}}(x, y) + \delta(x, y) \sigma^2
$$

Because it is essentially a full-rank GP, it is not treated as a real strict low-rank GP. It is a mixture of a parametrized low-rank process with a non-parametric, but closed-form, treatment of the expansion from subspace to full space.

```scala mdoc:silent
val shapeModel: PancakeDLRGP[_3D, UnstructuredPointsDomain[_3D], Point[_3D]] = model.neutralModel.shape
// the underlying strict low-rank model:
val shapeLRGP = shapeModel.gpModel
println("shape rank: " + shapeLRGP.rank +
   ", noise sdev=" + math.sqrt(shapeModel.noiseVariance))
// the same is true for color with type
val colorModel: PancakeDLRGP[_3D, UnstructuredPointsDomain[_3D], RGB] = model.neutralModel.color
println("color rank: " + colorModel.gpModel.rank +
   ", noise sdev=" + math.sqrt(colorModel.noiseVariance))

// the full rank GP is also available (it is still discrete)
val fullRankShapeGP = shapeModel.toDiscreteGaussianProcess
```

You might have noticed `neutralModel` above. A `MoMo` can consist of shape, color and optionally a model for facial expression. The expression model is of type `PancakeDLRGP[_3D, Vector[_3D]]` and describes the *deformation* of the neutral shape. The standard BFM does not have expression and we thus do not discuss it here. Most operations are generic and do not require you to do anything. However, you can specifically get a *neutral* or an *expression* model. Be aware, that  expression also adds a mean deformation and is thus never identical to the fully neutral model, even with all expression coefficients set to `0.0`.

```scala mdoc:silent
val neutralBFM: MoMoBasic = model.neutralModel
println("Neutral BFM has expressions: " + neutralBFM.hasExpressions)

println("Original BFM has expressions: " + model.hasExpressions)
for (bfmExpress: MoMoExpress <- model.expressionModel)
  println("expression model rank: " + bfmExpress.expression.gpModel.rank)
```