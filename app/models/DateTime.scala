package models

import java.time.format.DateTimeFormatter
import java.time.{ Instant, LocalDateTime, ZoneId }
import java.util.TimeZone

object DateTime {
  val shortFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM")
  val fullFormatter: DateTimeFormatter  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:dd")

  def printShort(d: LocalDateTime, tz: String): String = dateToTimeZone(d, tz).format(shortFormatter)

  def printFull(d: LocalDateTime, tz: String): String = dateToTimeZone(d, tz).format(fullFormatter)

  val defaultTz: ZoneId = TimeZone.getTimeZone("Etc/UTC").toZoneId

  def fromTimestamp(t: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochSecond(t), defaultTz)

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

  def now: Long = Instant.now().getEpochSecond

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
