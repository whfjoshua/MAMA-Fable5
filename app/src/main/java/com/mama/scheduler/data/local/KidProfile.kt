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

@Entity(tableName = "kid_profiles")
data class KidProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val colorHex: String, // e.g. "#FF5722"
    val dailyLimit: Int = 2
)

@Dao
interface KidProfileDao {
    @Query("SELECT * FROM kid_profiles ORDER BY name ASC")
    fun getAllProfilesFlow(): Flow<List<KidProfile>>

    @Query("SELECT * FROM kid_profiles ORDER BY name ASC")
    suspend fun getAllProfiles(): List<KidProfile>

    @Query("SELECT * FROM kid_profiles WHERE id = :id")
    suspend fun getProfileById(id: Int): KidProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: KidProfile): Long

    @Update
    suspend fun updateProfile(profile: KidProfile)

    @Delete
    suspend fun deleteProfile(profile: KidProfile)
}
