package com.mama.scheduler.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scheduled_events")
data class ScheduledEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val kidId: Int?,
    val kidName: String?,
    val startTime: Long,
    val endTime: Long,
    val dateString: String, // "yyyy-MM-dd"
    val travelBufferMinutes: Int = 0,
    val notes: String? = null,
    val googleEventId: String? = null,
    // Recurring event support
    val isRecurring: Boolean = false,
    val recurrenceRule: String? = null, // RRULE string, e.g. "RRULE:FREQ=WEEKLY;BYDAY=WE;COUNT=4"
    val recurringEndDate: String? = null,
    val parentEventId: Int? = null,
    // Photo highlights (comma-separated content:// URIs)
    val photoUris: String? = null
)

@Dao
interface ScheduledEventDao {
    @Query("SELECT * FROM scheduled_events ORDER BY startTime ASC")
    fun getAllEventsFlow(): Flow<List<ScheduledEvent>>

    @Query("SELECT * FROM scheduled_events ORDER BY startTime ASC")
    suspend fun getAllEvents(): List<ScheduledEvent>

    @Query("SELECT * FROM scheduled_events WHERE dateString = :dateString ORDER BY startTime ASC")
    suspend fun getEventsByDate(dateString: String): List<ScheduledEvent>

    @Query("SELECT * FROM scheduled_events WHERE kidId = :kidId AND dateString = :dateString")
    suspend fun getEventsForKidOnDate(kidId: Int, dateString: String): List<ScheduledEvent>

    @Query("SELECT * FROM scheduled_events WHERE kidId = :kidId")
    suspend fun getEventsForKid(kidId: Int): List<ScheduledEvent>

    @Query("SELECT * FROM scheduled_events WHERE id = :id")
    suspend fun getEventById(id: Int): ScheduledEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ScheduledEvent): Long

    @Update
    suspend fun updateEvent(event: ScheduledEvent)

    @Delete
    suspend fun deleteEvent(event: ScheduledEvent)
}
