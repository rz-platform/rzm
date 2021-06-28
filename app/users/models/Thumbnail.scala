package users.models

import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Paths
import javax.imageio.ImageIO

case class Thumbnail(name: String) {
  def this(name: String, size: Int) = this(Thumbnail.thumbImageName(name, size))
}

object Thumbnail {
  private val extension = "jpg"

  private def cropImageToSquare(src: BufferedImage): BufferedImage = {
    val (width, height) = (src.getWidth, src.getHeight)
    val (squareSide, x, y) = {
      if (width > height) {
        (height, (width - height) / 2, 0)
      } else {
        (width, 0, (height - width) / 2)
      }
    }
    src.getSubimage(x, y, squareSide, squareSide)
  }

  private def thumbImageName(name: String, size: Int): String = s"${name}_$size.$extension"

  def make(
    inputImgFile: File,
    size: Int,
    destination: String,
    destinationName: String
  ): Thumbnail = {
    val src = cropImageToSquare(ImageIO.read(inputImgFile))

    val img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    img.createGraphics.drawImage(
      src.getScaledInstance(size, size, Image.SCALE_SMOOTH),
      0,
      0,
      null
    )
    val outputFile = Paths.get(destination, thumbImageName(destinationName, size)).toFile
    ImageIO.write(img, extension, outputFile)
    Thumbnail(destinationName)
  }
}
