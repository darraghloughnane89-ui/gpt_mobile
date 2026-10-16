package dev.chungjungsoo.gptmobile.data.usage

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

data class UsageDraft(
    val platformUid: String,
    val model: String,
    val chatId: Int?,
    val inputTokens: Int,
    val outputTokens: Int,
    val estimated: Boolean,
    val createdAt: Long
)

interface UsageRecorder {
    suspend fun record(draft: UsageDraft)

    object None : UsageRecorder {
        override suspend fun record(draft: UsageDraft) = Unit
    }
}

@Singleton
class RoomUsageRecorder @Inject constructor(
    private val usageDao: UsageDao
) : UsageRecorder {
    override suspend fun record(draft: UsageDraft) {
        try {
            val prices = usageDao.getPrices()
            val price = UsageCost.resolve(draft.model, prices)
            val cost = if (price == null) {
                0L
            } else {
                UsageCost.costMicros(
                    draft.inputTokens,
                    draft.outputTokens,
                    price.inputMicrosPerMillion,
                    price.outputMicrosPerMillion
                )
            }
            usageDao.insertEvent(
                UsageEvent(
                    platformUid = draft.platformUid,
                    model = draft.model,
                    chatId = draft.chatId,
                    inputTokens = draft.inputTokens.coerceAtLeast(0),
                    outputTokens = draft.outputTokens.coerceAtLeast(0),
                    estimated = draft.estimated,
                    costMicros = cost,
                    createdAt = draft.createdAt
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Token accounting must not fail the chat that produced the tokens.
        }
    }
}
