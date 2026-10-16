/*
 * Observe, plan, act, verify.
 * Adapted from minitap-ai/mobile-use (Apache-2.0). Copyright 2025 Minitap.
 * Completion counts only when the next observation changed.
 * Unpredictable navigation actions run alone, then the loop looks again.
 */
package dev.chungjungsoo.gptmobile.data.phonecontrol.plan

import dev.chungjungsoo.gptmobile.data.phonecontrol.PhoneObservation
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.SafetyContext
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.SafetyDecision
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.SafetyGate
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.SafetyResult

data class LoopResult(
    val stopped: Boolean,
    val message: String,
    val acted: List<String>,
    val suspicious: Boolean
)

data class ActionConfirmation(
    val accepted: Boolean,
    val biometric: Boolean = false
)

class PhoneAgentLoop(
    private val safetyGate: SafetyGate,
    private val maxSteps: Int = 8
) {
    suspend fun run(
        goal: String,
        observe: suspend () -> PhoneObservation,
        plan: suspend (goal: String, observation: PhoneObservation, history: List<String>) -> List<PlannedAction>,
        act: suspend (PlannedAction) -> Boolean,
        confirm: suspend (SafetyResult) -> ActionConfirmation,
        onSuspicious: suspend () -> Unit = {},
        shouldStop: () -> Boolean = { false },
        contextFor: (PhoneObservation) -> SafetyContext
    ): LoopResult {
        val history = mutableListOf<String>()
        val acted = mutableListOf<String>()
        var suspicious = false
        var steps = 0
        var observation = observe()
        while (steps < maxSteps) {
            if (shouldStop()) {
                return LoopResult(true, "Phone control stopped.", acted, suspicious)
            }
            if (observation.hiddenInstructionDetected) {
                suspicious = true
                onSuspicious()
                history += "Hidden screen text was dropped and did not authorize anything."
            }
            val batch = isolateNavigation(plan(goal, observation, history))
            if (batch.isEmpty()) {
                return LoopResult(false, "The model did not choose an action.", acted, suspicious)
            }
            for (action in batch) {
                if (steps >= maxSteps) break
                val enriched = action.withElement(action.index?.let { index -> observation.elements.firstOrNull { it.index == index } })
                var context = contextFor(observation)
                var decision = safetyGate.evaluate(enriched, context)
                if (decision.suspicious) {
                    suspicious = true
                    onSuspicious()
                }
                if (decision.decision == SafetyDecision.NEEDS_CONFIRMATION) {
                    val confirmation = confirm(decision)
                    if (!confirmation.accepted) {
                        history += "You declined: ${decision.summary}"
                        continue
                    }
                    context = context.copy(
                        userConfirmed = true,
                        biometricConfirmed = context.biometricConfirmed || confirmation.biometric
                    )
                    decision = safetyGate.evaluate(enriched, context)
                }
                if (decision.decision != SafetyDecision.ALLOW) {
                    history += "${decision.decision}: ${decision.reason}"
                    if (decision.decision == SafetyDecision.USER_MUST_TAP || decision.decision == SafetyDecision.DENY) {
                        return LoopResult(decision.decision == SafetyDecision.DENY && decision.suspicious, decision.reason, acted, suspicious)
                    }
                    continue
                }
                val before = observation
                val changedByActuator = act(enriched)
                steps += 1
                acted += decision.summary
                observation = observe()
                val verified = verify(enriched, before, observation, changedByActuator)
                if (enriched.kind == PhoneActionKind.COMPLETE) {
                    return if (verified && enriched.success != false) {
                        LoopResult(false, enriched.message ?: "Done.", acted, suspicious)
                    } else {
                        history += "Verification failed: completion was not backed by an observed change."
                        LoopResult(false, "I will not mark that done without seeing a change.", acted, suspicious)
                    }
                }
                history += if (verified) "Verified: ${decision.summary}" else "Verification failed for ${decision.summary}"
            }
        }
        return LoopResult(false, "Stopped after $maxSteps steps.", acted, suspicious)
    }

    fun verify(action: PlannedAction, before: PhoneObservation, after: PhoneObservation, actuatorReportedChange: Boolean): Boolean {
        if (action.kind == PhoneActionKind.COMPLETE && action.success != false) {
            return evidence(before, after) || actuatorReportedChange
        }
        if (action.kind == PhoneActionKind.WAIT || action.kind == PhoneActionKind.COMPLETE) return true
        return evidence(before, after) || actuatorReportedChange
    }

    fun evidence(before: PhoneObservation, after: PhoneObservation): Boolean =
        before.treeText != after.treeText || before.packageName != after.packageName

    fun isolateNavigation(actions: List<PlannedAction>): List<PlannedAction> {
        val navigation = actions.firstOrNull { it.kind in NAVIGATION } ?: return actions
        return listOf(navigation)
    }

    companion object {
        val NAVIGATION = setOf(
            PhoneActionKind.BACK,
            PhoneActionKind.HOME,
            PhoneActionKind.LAUNCH_APP,
            PhoneActionKind.OPEN_INTENT,
            PhoneActionKind.APP_FUNCTION
        )
    }
}
