package infrastructure.repositories

import java.time.format.DateTimeFormatter
import java.time.{ Instant, LocalDateTime, ZoneId, ZoneOffset }
import java.util.TimeZone
import scala.collection.SortedMap
import scala.jdk.CollectionConverters.SetHasAsScala

object RzDateTime {
  private val localDateTime: LocalDateTime = LocalDateTime.now
  private val zoneList = ZoneId.getAvailableZoneIds.asScala.map { zoneId: String =>
    val id = ZoneId.of(zoneId)
    // LocalDateTime -> ZonedDateTime
    val zonedDateTime = localDateTime.atZone(id)
    // ZonedDateTime -> ZoneOffset
    val zoneOffset: ZoneOffset = zonedDateTime.getOffset
    //replace Z to +00:00
    val offset = zoneOffset.getId.replaceAll("Z", "+00:00")
    (id.toString, offset)
  }.toList.sortBy(t => t._2)

  /*
   * List of tz database time zones
   * See https://en.wikipedia.org/wiki/List_of_tz_database_time_zones
   */
  val zoneIds: SortedMap[String, String] = SortedMap.from(zoneList)

  val defaultTz: ZoneId = TimeZone.getTimeZone("Etc/UTC").toZoneId

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM HH:mm")

  def now: Long = Instant.now().getEpochSecond

  def formatDate(d: LocalDateTime, tz: String): String = dateToTimeZone(d, tz).format(formatter)

  def fromTimestamp(t: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(t), defaultTz)

  /*
   * Changing LocalDateTime based on time difference in current time zone vs. user time zone
   */
  def dateToTimeZone(date: LocalDateTime, tz: String): LocalDateTime = {
    val timezone = TimeZone.getTimeZone(tz)
    date
      .atZone(defaultTz)
      .withZoneSameInstant(timezone.toZoneId)
      .toLocalDateTime
  }

  def parseTimestamp(t: String): Long =
    try {
      t.toLong
    } catch {
      case _: NumberFormatException => now
    }

  def parseTimestamp(t: Option[String]): Option[Long] =
    t match {
      case Some(time) => Some(parseTimestamp(time))
      case _          => None
    }
}
