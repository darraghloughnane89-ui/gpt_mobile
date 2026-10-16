package dev.chungjungsoo.gptmobile.data.phonecontrol.safety

import dev.chungjungsoo.gptmobile.data.phonecontrol.plan.ActionSource
import dev.chungjungsoo.gptmobile.data.phonecontrol.plan.PhoneActionClassifier
import dev.chungjungsoo.gptmobile.data.phonecontrol.plan.PhoneActionKind
import dev.chungjungsoo.gptmobile.data.phonecontrol.plan.PlannedAction

enum class SafetyDecision {
    ALLOW,
    NEEDS_CONFIRMATION,
    USER_MUST_TAP,
    DENY
}

data class SafetyContext(
    val foregroundPackage: String,
    val appLabel: String = "",
    val allowlist: Set<String>,
    val secureWindow: Boolean = false,
    val screenText: String = "",
    val routineApproved: Boolean = false,
    val background: Boolean = false,
    val userConfirmed: Boolean = false,
    val biometricConfirmed: Boolean = false,
    val passwordFieldFocused: Boolean = false
)

data class SafetyResult(
    val decision: SafetyDecision,
    val reason: String,
    val summary: String,
    val suspicious: Boolean = false
)

class SafetyGate(
    private val rateLimiter: ActionRateLimiter = ActionRateLimiter()
) {
    fun evaluate(action: PlannedAction, context: SafetyContext): SafetyResult {
        val summary = action.summary()
        val instructional = InjectionGuard.isInstructional(context.screenText)
        val suspicious = instructional &&
            (
                PhoneActionClassifier.isHighRisk(action) ||
                    FinanceDenylist.isFinance(context.foregroundPackage, context.appLabel) ||
                    context.screenText.lowercase().contains("autofill")
                )
        if (action.source == ActionSource.SCREEN_TEXT || action.source == ActionSource.NOTIFICATION) {
            return SafetyResult(
                SafetyDecision.DENY,
                "Instructions found on the screen never authorize actions.",
                summary,
                true
            )
        }
        if (context.background && !RoutinePolicy.allowBackground(context.routineApproved)) {
            return SafetyResult(SafetyDecision.DENY, "No silent background run without a routine you approved.", summary, suspicious)
        }
        if (context.secureWindow && action.kind == PhoneActionKind.REQUEST_AUTOFILL) {
            return SafetyResult(SafetyDecision.DENY, "FLAG_SECURE screens are not captured or filled from a screenshot.", summary, true)
        }
        val finance = FinanceDenylist.isFinance(context.foregroundPackage, context.appLabel) ||
            FinanceDenylist.isFinance(action.packageName.orEmpty(), action.targetText.orEmpty())
        if (finance && PhoneActionClassifier.touchesForeground(action)) {
            return SafetyResult(
                SafetyDecision.USER_MUST_TAP,
                "Banking and finance apps need your tap. Robin will not do this unattended.",
                summary,
                suspicious || instructional
            )
        }
        if (action.kind == PhoneActionKind.TYPE && context.passwordFieldFocused) {
            return SafetyResult(SafetyDecision.DENY, "Robin never types a password. Use autofill.", summary, suspicious)
        }
        if (PhoneActionClassifier.touchesForeground(action)) {
            val target = action.packageName?.takeIf { action.kind == PhoneActionKind.LAUNCH_APP || action.kind == PhoneActionKind.APP_FUNCTION }
                ?: context.foregroundPackage
            if (!AllowlistPolicy(context.allowlist).permits(target)) {
                return SafetyResult(SafetyDecision.DENY, "That app is not on the allowlist.", summary, suspicious)
            }
        }
        if (!rateLimiter.allow()) {
            return SafetyResult(SafetyDecision.DENY, "Too many actions. The run is rate limited.", summary, suspicious)
        }
        if (PhoneActionClassifier.isCredential(action)) {
            return if (context.biometricConfirmed) {
                SafetyResult(SafetyDecision.ALLOW, "Autofill requested. The model does not receive the secret.", summary, suspicious)
            } else {
                SafetyResult(SafetyDecision.NEEDS_CONFIRMATION, "That action needs a fingerprint. Safety first, no drama.", summary, suspicious)
            }
        }
        val needsConfirmation = PhoneActionClassifier.isHighRisk(action) ||
            action.source == ActionSource.VISION_ONLY ||
            action.source == ActionSource.LOW_OPACITY ||
            action.lowConfidence
        if (needsConfirmation && !context.userConfirmed && !context.biometricConfirmed) {
            val reason = if (suspicious) {
                "Screen text looked risky. Confirm the action before I proceed."
            } else {
                "Confirm this action: $summary"
            }
            return SafetyResult(SafetyDecision.NEEDS_CONFIRMATION, reason, summary, suspicious)
        }
        return SafetyResult(SafetyDecision.ALLOW, "Allowed.", summary, suspicious)
    }
}
