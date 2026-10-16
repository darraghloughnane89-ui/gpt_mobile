package dev.chungjungsoo.gptmobile.data.phonecontrol.safety

import dev.chungjungsoo.gptmobile.data.phonecontrol.plan.PhoneActionKind
import dev.chungjungsoo.gptmobile.data.phonecontrol.plan.PlannedAction

object FinanceDenylist {
    val packages = setOf(
        "com.bankofireland.mobilebanking",
        "com.bankofireland.aib",
        "com.aib.mobilebanking",
        "ie.aib.mobile",
        "com.permanenttsb.mobilebanking",
        "ie.permanenttsb.mobile",
        "com.ulsterbank.mobile",
        "com.revolut.revolut",
        "com.revolut.business",
        "com.paypal.android.p2pmobile",
        "com.transferwise.android",
        "com.starlingbank.android",
        "com.n26.android",
        "com.coinbase.android",
        "com.binance.dev",
        "com.google.android.apps.walletnfcrel",
        "com.google.android.apps.nbu.paisa.user",
        "ie.bankofireland.mobilebanking",
        "com.venmo",
        "com.squareup.cash"
    )

    private val categoryWords = listOf("bank", "finance", "wallet", "broker", "credit union", "payments")

    private val brands = listOf(
        "revolut",
        "paypal",
        "monzo",
        "starling",
        "coinbase",
        "binance",
        "venmo",
        "n26",
        "transferwise",
        "wise",
        "aib",
        "ulster bank",
        "permanent tsb",
        "bank of ireland",
        "chase",
        "wellsfargo",
        "wells fargo",
        "barclays",
        "hsbc",
        "monese",
        "robinhood",
        "cash app",
        "squareup.cash",
        "google pay",
        "google wallet",
        "klarna",
        "sofi",
        "chime"
    )

    private val brandRegex = Regex(brands.joinToString("|") { "\\b${Regex.escape(it)}\\b" })

    fun isFinance(packageName: String, label: String = ""): Boolean {
        val normalized = packageName.trim().lowercase()
        val haystack = "$normalized ${label.lowercase()}"
        if (packages.any { known ->
                normalized == known || normalized.startsWith("$known.") || haystack.contains(known)
            }
        ) {
            return true
        }
        if (categoryWords.any { haystack.contains(it) }) return true
        return brandRegex.containsMatchIn(haystack)
    }
}

object ActionDestinations {
    private val destinationKinds = setOf(
        PhoneActionKind.LAUNCH_APP,
        PhoneActionKind.OPEN_INTENT,
        PhoneActionKind.APP_FUNCTION
    )
    private val explicitPackage = Regex(
        """(?i)(?:package=|component=|id=|package:)([a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*)+)"""
    )
    private val wholePackage = Regex("""^[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*)+$""")

    fun packages(action: PlannedAction): Set<String> {
        if (action.kind !in destinationKinds) return emptySet()
        val found = linkedSetOf<String>()
        listOfNotNull(action.packageName, action.text).forEach { raw ->
            found += candidates(raw)
        }
        return found
    }

    private fun candidates(raw: String): Set<String> {
        val found = linkedSetOf<String>()
        val trimmed = raw.trim()
        if (wholePackage.matches(trimmed)) found += trimmed
        explicitPackage.findAll(raw).forEach { match ->
            found += match.groupValues[1]
        }
        raw.split(Regex("[\\s/]+")).forEach { part ->
            val cleaned = part.trim().trimEnd(';')
            if (wholePackage.matches(cleaned)) found += cleaned
        }
        return found
    }
}

class AllowlistPolicy(private val allowedPackages: Set<String>) {
    fun permits(packageName: String): Boolean {
        val normalized = packageName.trim().lowercase()
        if (normalized.isBlank() || normalized.any { it.isWhitespace() }) return false
        return allowedPackages.any { it.trim().lowercase() == normalized }
    }

    fun permitsAny(packages: Set<String>): Boolean = packages.isNotEmpty() && packages.all(::permits)
}

class ActionRateLimiter(
    private val maxPerWindow: Int = 12,
    private val windowMillis: Long = 60_000,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val stamps = ArrayDeque<Long>()

    fun allow(now: Long = clock()): Boolean {
        while (stamps.isNotEmpty() && now - stamps.first() >= windowMillis) {
            stamps.removeFirst()
        }
        if (stamps.size >= maxPerWindow) return false
        stamps.addLast(now)
        return true
    }
}

object RoutinePolicy {
    fun allowBackground(approvedRoutine: Boolean): Boolean = approvedRoutine
}
