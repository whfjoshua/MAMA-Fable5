package com.mama.scheduler.domain

import com.mama.scheduler.core.DateUtils
import com.mama.scheduler.data.local.KidProfile
import com.mama.scheduler.data.local.ScheduledEvent
import javax.inject.Inject
import javax.inject.Singleton

enum class ConflictType { OVERLAP, TRAVEL_BUFFER, DAILY_LIMIT }

data class ConflictInfo(
    val type: ConflictType,
    val message: String,
    val offendingEventTitle: String? = null
)

/** Pure conflict evaluation: overlaps, travel buffers, and per-kid daily limits. */
@Singleton
class ConflictDetector @Inject constructor() {

    fun check(
        startTimeMillis: Long,
        endTimeMillis: Long,
        travelBufferMins: Int,
        kidId: Int?,
        dateString: String,
        events: List<ScheduledEvent>,
        profiles: List<KidProfile>,
        excludeEventId: Int? = null
    ): List<ConflictInfo> {
        val conflicts = mutableListOf<ConflictInfo>()

        for (event in events) {
            if (event.id == excludeEventId) continue

            // 1. Direct time overlap
            val directOverlap = startTimeMillis < event.endTime && event.startTime < endTimeMillis
            if (directOverlap) {
                val who = event.kidName?.let { "$it's" } ?: "the family's"
                conflicts.add(
                    ConflictInfo(
                        type = ConflictType.OVERLAP,
                        message = "Clashes with $who \"${event.title}\" (${DateUtils.formatTimeRange(event.startTime, event.endTime)}).",
                        offendingEventTitle = event.title
                    )
                )
                continue
            }

            // 2. Travel-buffer clash
            val bufferA = travelBufferMins * 60_000L
            val bufferB = event.travelBufferMinutes * 60_000L
            val travelConflict =
                startTimeMillis < (event.endTime + bufferB) && event.startTime < (endTimeMillis + bufferA)
            if (travelConflict) {
                conflicts.add(
                    ConflictInfo(
                        type = ConflictType.TRAVEL_BUFFER,
                        message = "Travel time overlaps with \"${event.title}\" (${travelBufferMins}m / ${event.travelBufferMinutes}m buffers).",
                        offendingEventTitle = event.title
                    )
                )
            }
        }

        // 3. Daily-limit check
        if (kidId != null) {
            val profile = profiles.firstOrNull { it.id == kidId }
            if (profile != null) {
                val count = events.count {
                    it.kidId == kidId && it.dateString == dateString && it.id != excludeEventId
                }
                if (count >= profile.dailyLimit) {
                    conflicts.add(
                        ConflictInfo(
                            type = ConflictType.DAILY_LIMIT,
                            message = "${profile.name} already has $count of ${profile.dailyLimit} daily activities booked.",
                        )
                    )
                }
            }
        }

        return conflicts
    }
}
