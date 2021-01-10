package models

import java.time.format.DateTimeFormatter
import java.time.{ Instant, LocalDateTime }

object DateTime {
  val shortFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM")
  val fullFormatter: DateTimeFormatter  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:dd")

  def isToday(d: LocalDateTime): Boolean = {
    val today = LocalDateTime.now();
    if (today.getYear == d.getYear && today.getDayOfYear == d.getDayOfYear) {
      true
    } else {
      false
    }
  }

  def printShort(d: LocalDateTime): String =
    if (isToday(d)) {
      "Today" // TODO: localize
    } else {
      d.format(shortFormatter)
    }

  def printFull(d: LocalDateTime): String = d.format(fullFormatter)

  def now: Long = Instant.now().getEpochSecond

  def parseTimestamp(t: String): Long =
    try {
      t.toLong
    } catch {
      case _: NumberFormatException => now
    }
}
