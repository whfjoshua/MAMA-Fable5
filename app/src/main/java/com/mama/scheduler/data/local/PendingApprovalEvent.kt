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

@Entity(tableName = "pending_approval_events")
data class PendingApprovalEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val rawInput: String,
    val title: String,
    val kidName: String?,
    val kidId: Int?,
    val startTime: Long,
    val endTime: Long,
    val dateString: String,
    val travelBufferMinutes: Int = 0,
    val status: String = "PENDING",
    val creationTime: Long = System.currentTimeMillis(),
    val isRecurring: Boolean = false,
    val recurrenceRule: String? = null,
    val recurringEndDate: String? = null
)

@Dao
interface PendingApprovalEventDao {
    @Query("SELECT * FROM pending_approval_events WHERE status = 'PENDING' ORDER BY creationTime DESC")
    fun getPendingEventsFlow(): Flow<List<PendingApprovalEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingEvent(event: PendingApprovalEvent): Long

    @Update
    suspend fun updatePendingEvent(event: PendingApprovalEvent)

    @Delete
    suspend fun deletePendingEvent(event: PendingApprovalEvent)
}
