package services.images

import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Paths

import javax.imageio.ImageIO

object ImageService {
  val defaultExtension = "jpg"

  private def cropImageToSquare(src: BufferedImage): BufferedImage = {
    val (width, height) = (src.getWidth, src.getHeight)
    val (squareSide, x) = {
      if (width > height) {
        (height, (width - height) / 2)
      } else {
        (width, (height - width) / 2)
      }
    }
    src.getSubimage(x, 0, squareSide, squareSide)
  }

  def createSquaredThumbnail(
      inputImgFile: File,
      width: Int,
      height: Int,
      destination: String,
      destinationName: String
  ): File = {
    val img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    img.createGraphics.drawImage(
      ImageIO.read(inputImgFile).getScaledInstance(width, height, Image.SCALE_SMOOTH),
      0,
      0,
      null
    )
    val squareImg = cropImageToSquare(img)

    val outputFile = Paths.get(destination, s"$destinationName.$defaultExtension").toFile
    ImageIO.write(squareImg, defaultExtension, outputFile)
    outputFile
  }
}
