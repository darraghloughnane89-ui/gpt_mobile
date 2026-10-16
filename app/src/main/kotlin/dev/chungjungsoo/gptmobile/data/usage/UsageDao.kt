package dev.chungjungsoo.gptmobile.data.usage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage_events ORDER BY created_at DESC")
    fun observeEvents(): Flow<List<UsageEvent>>

    @Query("SELECT * FROM model_prices ORDER BY model_key ASC")
    fun observePrices(): Flow<List<ModelPrice>>

    @Query("SELECT * FROM model_prices")
    suspend fun getPrices(): List<ModelPrice>

    @Insert
    suspend fun insertEvent(event: UsageEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrice(price: ModelPrice)
}
