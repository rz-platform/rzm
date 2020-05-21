import org.scalatest._
import services.PathService

import scala.util.Random

class FunctionalPathTest extends FlatSpec with Matchers {
  def getRandomString: String =
    java.util.UUID.randomUUID.toString

  def getRandomNumber: Int = Random.between(2, 100)

  "PathService" should "return an array" in {
    val len    = getRandomNumber
    val path   = (1 to len).map(_ => getRandomString).mkString("/")
    val result = PathService.buildTreeFromPath(path)
    result shouldBe a[Array[_]]
    result.length shouldBe len
  }

  "PathService" should "return a one dir" in {
    val path   = getRandomString
    val result = PathService.buildTreeFromPath(path)
    result shouldBe a[Array[_]]
    result.length shouldBe 1
  }

  "PathService" should "return an array without filename" in {
    val len    = getRandomNumber
    val path   = (1 to len).map(_ => getRandomString).mkString("/")
    val result = PathService.buildTreeFromPath(path, isFile = true)
    result shouldBe a[Array[_]]
    result.length shouldBe len - 1
  }

  "PathService" should " with file in root return an empty array" in {
    val path   = getRandomString
    val result = PathService.buildTreeFromPath(path, isFile = true)
    result shouldBe a[Array[_]]
    result.length shouldBe 0
  }

  "PathService" should "in roout should return empty array" in {
    val result = PathService.buildTreeFromPath(".")
    result shouldBe a[Array[_]]
    result.length shouldBe 0
  }
}
