package dev.chungjungsoo.gptmobile.data.phonecontrol.safety

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
        "ie.bankofireland.mobilebanking"
    )

    private val categoryWords = listOf("bank", "finance", "wallet", "broker", "credit union", "payments")

    fun isFinance(packageName: String, label: String = ""): Boolean {
        val normalized = packageName.lowercase()
        if (packages.any { it == normalized || normalized.startsWith("$it.") }) return true
        val haystack = "$normalized ${label.lowercase()}"
        return categoryWords.any { haystack.contains(it) }
    }
}

class AllowlistPolicy(private val allowedPackages: Set<String>) {
    fun permits(packageName: String): Boolean = packageName.isNotBlank() && packageName in allowedPackages

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
