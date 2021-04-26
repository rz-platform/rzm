package repositories

import java.time.{ LocalDateTime, ZoneId }
import java.util

object TimezoneOffsetRepository {

  private val allZoneIdsAndItsOffSet: util.HashMap[String, String] = {
    val result        = new util.HashMap[String, String]
    val localDateTime = LocalDateTime.now

    for (zoneId: String <- ZoneId.getAvailableZoneIds.toArray()) {
      val id = ZoneId.of(zoneId)
      // LocalDateTime -> ZonedDateTime
      val zonedDateTime = localDateTime.atZone(id)
      // ZonedDateTime -> ZoneOffset
      val zoneOffset = zonedDateTime.getOffset
      //replace Z to +00:00
      val offset = zoneOffset.getId.replaceAll("Z", "+00:00")
      result.put(id.toString, offset)
    }
    result
  }

  val sortedMap = new util.LinkedHashMap[String, String]

  allZoneIdsAndItsOffSet.entrySet.stream
    .sorted(util.Map.Entry.comparingByValue[String, String].reversed)
    .forEachOrdered((e: util.Map.Entry[String, String]) => sortedMap.put(e.getKey, e.getValue))
}
