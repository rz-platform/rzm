package repositories

import java.time.{ LocalDateTime, ZoneId, ZoneOffset }
import scala.collection.SortedMap
import scala.jdk.CollectionConverters.CollectionHasAsScala

object TimezoneOffsetRepository {
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
}
