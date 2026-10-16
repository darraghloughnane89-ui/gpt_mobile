package dev.chungjungsoo.gptmobile.data.usage

import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class UsageRepository @Inject constructor(
    private val usageDao: UsageDao
) {
    fun observeSummary(): Flow<UsageSummary> = combine(usageDao.observeEvents(), usageDao.observePrices()) { events, prices ->
        UsageSummaryCalculator.summarize(events, prices, ZoneId.systemDefault())
    }

    fun observePrices(): Flow<List<ModelPrice>> = usageDao.observePrices()

    suspend fun savePrice(modelKey: String, inputUsdPerMillion: String, outputUsdPerMillion: String, updatedAt: Long): Boolean {
        val key = modelKey.trim()
        val input = UsageCost.usdPerMillionToMicros(inputUsdPerMillion)
        val output = UsageCost.usdPerMillionToMicros(outputUsdPerMillion)
        if (key.isEmpty() || input == null || output == null) return false
        usageDao.upsertPrice(
            ModelPrice(
                modelKey = key,
                inputMicrosPerMillion = input,
                outputMicrosPerMillion = output,
                updatedAt = updatedAt
            )
        )
        return true
    }
}
