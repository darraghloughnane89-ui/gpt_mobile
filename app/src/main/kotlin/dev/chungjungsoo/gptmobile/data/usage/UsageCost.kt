package dev.chungjungsoo.gptmobile.data.usage

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

object ModelPriceCatalog {
    /**
     * Starter USD prices per one million tokens, stored as micros ($1 = 1_000_000).
     * They are estimates the user can edit. They are not an invoice.
     */
    val defaults: List<ModelPrice> = listOf(
        price("gpt-4o", "2.50", "10.00"),
        price("gpt-4o-mini", "0.15", "0.60"),
        price("gpt-4.1", "2.00", "8.00"),
        price("gpt-4.1-mini", "0.40", "1.60"),
        price("claude-sonnet-4-5", "3.00", "15.00"),
        price("claude-3-5-haiku-latest", "0.80", "4.00"),
        price("gemini-2.5-pro", "1.25", "10.00"),
        price("gemini-2.5-flash", "0.15", "0.60"),
        price("llama-3.3-70b-versatile", "0.59", "0.79")
    )

    fun insertStatements(): List<String> = defaults.map { price ->
        "INSERT OR IGNORE INTO `model_prices` (`model_key`, `input_micros_per_million`, `output_micros_per_million`, `updated_at`) " +
            "VALUES ('${price.modelKey}', ${price.inputMicrosPerMillion}, ${price.outputMicrosPerMillion}, 0)"
    }

    private fun price(model: String, inputUsd: String, outputUsd: String) = ModelPrice(
        modelKey = model,
        inputMicrosPerMillion = UsageCost.usdPerMillionToMicros(inputUsd) ?: 0L,
        outputMicrosPerMillion = UsageCost.usdPerMillionToMicros(outputUsd) ?: 0L,
        updatedAt = 0L
    )
}

object UsageCost {
    fun costMicros(inputTokens: Int, outputTokens: Int, inputPerMillion: Long, outputPerMillion: Long): Long {
        return (inputTokens.toLong() * inputPerMillion + outputTokens.toLong() * outputPerMillion) / 1_000_000L
    }

    fun resolve(model: String, prices: List<ModelPrice>): ModelPrice? {
        val normalized = model.trim()
        if (normalized.isEmpty()) return null
        prices.firstOrNull { it.modelKey.equals(normalized, ignoreCase = true) }?.let { return it }
        val leaf = normalized.substringAfterLast('/')
        return prices.firstOrNull { it.modelKey.equals(leaf, ignoreCase = true) }
    }

    fun usdPerMillionToMicros(text: String): Long? {
        val value = text.trim().toBigDecimalOrNull() ?: return null
        if (value < BigDecimal.ZERO) return null
        return value.multiply(BigDecimal(1_000_000)).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    fun formatUsd(micros: Long): String = String.format(Locale.US, "$%.4f", micros / 1_000_000.0)

    fun liveCost(event: UsageEvent, prices: List<ModelPrice>): Long {
        val price = resolve(event.model, prices) ?: return event.costMicros
        return costMicros(event.inputTokens, event.outputTokens, price.inputMicrosPerMillion, price.outputMicrosPerMillion)
    }
}

data class ModelUsageTotal(
    val model: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val costMicros: Long,
    val estimatedEvents: Int
)

data class PeriodUsageTotal(
    val label: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val costMicros: Long
)

data class UsageSummary(
    val byModel: List<ModelUsageTotal>,
    val byDay: List<PeriodUsageTotal>,
    val byMonth: List<PeriodUsageTotal>,
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val totalCostMicros: Long
)

object UsageSummaryCalculator {
    fun summarize(events: List<UsageEvent>, prices: List<ModelPrice>, zone: ZoneId): UsageSummary {
        val priced = events.map { event -> event to UsageCost.liveCost(event, prices) }
        val byModel = priced.groupBy { it.first.model.ifBlank { "unknown" } }
            .map { (model, rows) ->
                ModelUsageTotal(
                    model = model,
                    inputTokens = rows.sumOf { it.first.inputTokens.toLong() },
                    outputTokens = rows.sumOf { it.first.outputTokens.toLong() },
                    costMicros = rows.sumOf { it.second },
                    estimatedEvents = rows.count { it.first.estimated }
                )
            }
            .sortedByDescending { it.costMicros }
        val byDay = periodTotals(priced, zone) { date -> date.toString() }
        val byMonth = periodTotals(priced, zone) { date -> "${date.year}-${date.monthValue.toString().padStart(2, '0')}" }
        return UsageSummary(
            byModel = byModel,
            byDay = byDay,
            byMonth = byMonth,
            totalInputTokens = priced.sumOf { it.first.inputTokens.toLong() },
            totalOutputTokens = priced.sumOf { it.first.outputTokens.toLong() },
            totalCostMicros = priced.sumOf { it.second }
        )
    }

    private fun periodTotals(
        priced: List<Pair<UsageEvent, Long>>,
        zone: ZoneId,
        label: (LocalDate) -> String
    ): List<PeriodUsageTotal> = priced.groupBy { label(day(it.first.createdAt, zone)) }
        .map { (period, rows) ->
            PeriodUsageTotal(
                label = period,
                inputTokens = rows.sumOf { it.first.inputTokens.toLong() },
                outputTokens = rows.sumOf { it.first.outputTokens.toLong() },
                costMicros = rows.sumOf { it.second }
            )
        }
        .sortedByDescending { it.label }

    fun day(epochSeconds: Long, zone: ZoneId): LocalDate = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate()
}
