package dev.chungjungsoo.gptmobile.data.phonecontrol

import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.FinanceDenylist
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.InjectionGuard
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.Redaction
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.ScreenshotPolicy
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.UntrustedText
import dev.chungjungsoo.gptmobile.data.phonecontrol.tree.IndexedTreeFormatter

object PhoneObservationBuilder {
    fun build(
        root: UiNode?,
        packageName: String,
        appLabel: String,
        secureWindow: Boolean,
        screenWidth: Int,
        screenHeight: Int,
        visionRequested: Boolean
    ): PhoneObservation {
        val redacted = root?.let(Redaction::redactNode)
        val hidden = collectHidden(redacted)
        val visible = redacted?.let(::stripHidden)
        val formatted = IndexedTreeFormatter(screenWidth, screenHeight).format(
            visible,
            IndexedTreeFormatter.PhoneState(
                packageName = packageName,
                appLabel = appLabel,
                focusedText = focusedText(visible),
                keyboardVisible = containsEditable(visible)
            )
        )
        val passwordVisible = containsPassword(redacted)
        val finance = FinanceDenylist.isFinance(packageName, appLabel)
        val attachScreenshot = visionRequested &&
            !ScreenshotPolicy.preferTree(formatted.elements.isNotEmpty()) &&
            ScreenshotPolicy.allowCapture(secureWindow, finance, passwordVisible)
        return PhoneObservation(
            packageName = packageName,
            appLabel = appLabel,
            treeText = UntrustedText.wrap("screen", formatted.text),
            elements = formatted.elements,
            focusedText = if (focusedIsPassword(visible)) Redaction.REDACTED else formatted.focusedText,
            secureWindow = secureWindow,
            passwordFieldVisible = passwordVisible,
            hiddenInstructionDetected = hidden.any(InjectionGuard::isInstructional),
            screenshotAttached = attachScreenshot
        )
    }

    private fun stripHidden(node: UiNode): UiNode? {
        if (node.lowConfidence && (node.text.isNotBlank() || node.contentDescription.isNotBlank())) return null
        return node.copy(children = node.children.mapNotNull(::stripHidden))
    }

    private fun collectHidden(node: UiNode?): List<String> {
        if (node == null) return emptyList()
        val own = if (node.lowConfidence) listOf(node.text, node.contentDescription) else emptyList()
        return own + node.children.flatMap { collectHidden(it) }
    }

    private fun focusedText(node: UiNode?): String {
        if (node == null) return ""
        if (node.isFocused && !node.isPassword) return node.text
        return node.children.firstNotNullOfOrNull { focusedText(it).takeIf(String::isNotBlank) }.orEmpty()
    }

    private fun focusedIsPassword(node: UiNode?): Boolean {
        if (node == null) return false
        if (node.isFocused && node.isPassword) return true
        return node.children.any(::focusedIsPassword)
    }

    private fun containsEditable(node: UiNode?): Boolean {
        if (node == null) return false
        return node.isEditable || node.children.any(::containsEditable)
    }

    private fun containsPassword(node: UiNode?): Boolean {
        if (node == null) return false
        return node.isPassword || node.children.any(::containsPassword)
    }
}
