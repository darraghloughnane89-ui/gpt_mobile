package dev.chungjungsoo.gptmobile.data.phonecontrol.plan

import dev.chungjungsoo.gptmobile.data.phonecontrol.IndexedElement
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.Redaction
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.TextCanonicalizer

enum class ActionSource {
    MODEL_TOOL,
    SCREEN_TEXT,
    NOTIFICATION,
    VISION_ONLY,
    LOW_OPACITY
}

enum class PhoneActionKind {
    TAP,
    TYPE,
    SWIPE,
    SCROLL,
    BACK,
    HOME,
    LAUNCH_APP,
    OPEN_INTENT,
    APP_FUNCTION,
    REQUEST_AUTOFILL,
    REPLY_NOTIFICATION,
    WAIT,
    COMPLETE
}

data class PlannedAction(
    val kind: PhoneActionKind,
    val index: Int? = null,
    val text: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val x2: Int? = null,
    val y2: Int? = null,
    val packageName: String? = null,
    val button: String? = null,
    val success: Boolean? = null,
    val message: String? = null,
    val source: ActionSource = ActionSource.MODEL_TOOL,
    val targetText: String? = null,
    val resourceId: String? = null,
    val lowConfidence: Boolean = false
) {
    fun summary(): String = Redaction.redactSecrets(rawSummary())

    private fun rawSummary(): String {
        val target = targetText?.takeIf { it.isNotBlank() } ?: resourceId.orEmpty()
        return when (kind) {
            PhoneActionKind.TAP -> "Tap ${index ?: "point"} $target".trim()
            PhoneActionKind.TYPE -> "Type into ${index ?: "the focused field"}"
            PhoneActionKind.SWIPE -> "Swipe"
            PhoneActionKind.SCROLL -> "Scroll"
            PhoneActionKind.BACK -> "Back"
            PhoneActionKind.HOME -> "Home"
            PhoneActionKind.LAUNCH_APP -> "Open ${packageName ?: text.orEmpty()}".trim()
            PhoneActionKind.OPEN_INTENT -> "Open intent ${text.orEmpty()}".trim()
            PhoneActionKind.APP_FUNCTION -> "Run app function ${text.orEmpty()} in ${packageName.orEmpty()}".trim()
            PhoneActionKind.REQUEST_AUTOFILL -> "Request autofill"
            PhoneActionKind.REPLY_NOTIFICATION -> "Reply to notification"
            PhoneActionKind.WAIT -> "Wait"
            PhoneActionKind.COMPLETE -> "Complete success=$success"
        }
    }

    fun withElement(element: IndexedElement?): PlannedAction {
        if (element == null) return this
        return copy(
            targetText = targetText ?: element.text,
            resourceId = resourceId ?: element.resourceId,
            lowConfidence = lowConfidence || element.lowConfidence,
            source = if (element.lowConfidence && source == ActionSource.MODEL_TOOL) ActionSource.LOW_OPACITY else source
        )
    }
}

object PhoneActionClassifier {
    private val paymentWords = listOf(
        "pay",
        "payment",
        "purchase",
        "buy",
        "checkout",
        "transfer",
        "price",
        "wire",
        "donate",
        "withdraw",
        "deposit"
    )
    private val messageWords = listOf("send", "post", "reply", "share", "publish")
    private val deleteWords = listOf("delete", "remove", "erase", "trash", "discard", "wipe", "shred")
    private val installWords = listOf("install", "uninstall", "update app", "download", "upgrade", "sideload")

    fun isPayment(action: PlannedAction): Boolean = containsAny(action, paymentWords)

    fun isMessage(action: PlannedAction): Boolean = action.kind == PhoneActionKind.REPLY_NOTIFICATION || containsAny(action, messageWords)

    fun isDelete(action: PlannedAction): Boolean = containsAny(action, deleteWords)

    fun isInstall(action: PlannedAction): Boolean = containsAny(action, installWords)

    fun isCredential(action: PlannedAction): Boolean = action.kind == PhoneActionKind.REQUEST_AUTOFILL

    fun isHighRisk(action: PlannedAction): Boolean = isPayment(action) || isMessage(action) || isDelete(action) || isInstall(action) || isCredential(action)

    fun touchesForeground(action: PlannedAction): Boolean = when (action.kind) {
        PhoneActionKind.BACK, PhoneActionKind.HOME, PhoneActionKind.WAIT, PhoneActionKind.COMPLETE -> false
        else -> true
    }

    private fun containsAny(action: PlannedAction, words: List<String>): Boolean {
        val haystack = TextCanonicalizer.fold(
            listOfNotNull(action.targetText, action.text, action.message, action.resourceId).joinToString(" ")
        ).lowercase()
        return words.any { word -> haystack.contains(word) }
    }
}
