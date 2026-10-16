package dev.chungjungsoo.gptmobile.data.drive

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DriveSyncDao {
    @Query("SELECT * FROM drive_sync_state WHERE sync_id = 1")
    suspend fun get(): DriveSyncState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: DriveSyncState)
}
