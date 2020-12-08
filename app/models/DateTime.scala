package models

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
}
