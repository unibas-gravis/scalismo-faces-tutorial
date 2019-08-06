# Images

Computer graphics deals with the artificial generation of images. In this first article, you can explore how the Scalismo-faces framework handles images. Our approach is different from what you might know from other graphics frameworks. To us, images are highly functional and generic structures. You will see how such an understanding can simplify working with images drastically.

The basic structure is the `PixelImage`. It is a *function* that maps pixel indices `(x, y)` to values. An image is thus simply a function on a grid - a two-dimensional data structure. The type of the function value is fully generic. To keep the interpretation closer to what you intuitively know about images, we suggest you start with `Double`. You can use any function to construct an image. For optimal behavior, it should be side-effect-free.

```scala
import scalismo.faces.image._
val width: Int = 200
val height: Int = 100
val f: (Int,Int) => Double = (x, y) => x/200.0
val img: PixelImage[Double] = PixelImage(width,height, f)
```

Images can be displayed in an `ImagePanel` of the GUI framework.

```scala
import scalismo.faces.gui._
import GUIBlock._

ImagePanel(img).displayIn("Image")
```

The image is a function of pixel coordinates (grid cells) to values. Access is possible through the apply method.

```scala
println(img(50,45))
```

If you have data, you can also construct an image from that. However, as data is typically stored linearly, you need to specify the ordering of rows and columns using a `PixelImageDomain`.

```scala
import scalismo.utils._

val rnd = Random(98)
val randomValues = IndexedSeq.fill(40000)(rnd.scalaRandom.nextDouble())
val randomImage = PixelImage(ColumnMajorImageDomain(200, 200), randomValues)

ImagePanel(randomImage).displayIn("Image")
```

Contrary to having pre-calculated data, you can also specify lazy images where each value is calculated only when required. These images are created as a *view* - just as with standard scala collections.

```scala
val lazyImage = PixelImage.view(100, 100, (x, y) => {
  Thread.sleep(1000)
  y/100.0
})
```

The defined image from above is very expensive to calculate, its full evaluation requires many seconds. But still we can access a single pixel in only the time needed to compute the single pixel value.

```scala
println("value at (40, 70) is " + lazyImage(40, 70))
```

Images support also the `map` operation:

```scala
val imgSqrt = img.map(math.sqrt)
```

To easily compare the two images, we stack the image panels before displaying them in a single window:

```scala
val stackedImages = stack(
  ImagePanel(img),
  ImagePanel(imgSqrt)
)
stackedImages.displayIn("Image")
```

Transforming can also depend on position when using `mapWithIndex`. Invert every 10th column:

```scala
val imgInvertedColumns = img.mapWithIndex((v, x, y) => if (x % 10 == 0) 1.0 - v else v)
ImagePanel(imgInvertedColumns).displayIn("Image")
```

We can add color to the gray world of intensity images. Just change the type of the image. Color is typically represented by three spectral samples, so called *channels*. A standard choice is the RGB model, where red, green and blue parts of the spectrum are specified. There are also other color representations, such as HSV.

```scala
import scalismo.color.{RGB, RGBA}
import scalismo.faces.color.HSV

// a more colorful gradient image
val colorful: PixelImage[RGB] = PixelImage(200, 100, (x, y) => RGB(x/200.0, y/100.0, 1.0 - x/200.0))
ImagePanel(colorful).displayIn("Image")
```

The RGB color model can be extended by opacity information. Use `RGBA` to express transparency with an alpha channel. A value of 1.0 is fully opaque and 0.0 is fully transparent.

```scala
val color = RGB(1.0, 0.5, 0.25)
val semitransparent = RGBA(1.0, 0.5, 0.25, 0.5)
```

Color images are typically loaded from disk or stored. IO functionality is available for RGB, RGBA (RGB and transparency) and Double types. Although our `PixelImage`s can have any type, disk-based image file formats typically require a color interpretation.

```scala
import scalismo.faces.io.PixelImageIO
import java.io.File

// .get at end checks for exceptions
PixelImageIO.write(colorful, new File("colorgradient.png")).get
val colGradIn = PixelImageIO.read[RGB](new File("colorgradient.png")).get

stack(
    ImagePanel(colorful),
    ImagePanel(colGradIn)
).displayIn("Image")
```

