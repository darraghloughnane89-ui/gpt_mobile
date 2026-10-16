package dev.chungjungsoo.gptmobile.data.phonecontrol

import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.FinanceDenylist
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.InjectionGuard
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.Redaction
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.ScreenshotPolicy
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.SecureWindowDetector
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
        visionRequested: Boolean,
        flagSecure: Boolean = false,
        screenshotErrorCode: Int? = null,
        captureVerifiedSafe: Boolean = false
    ): PhoneObservation {
        val rootPackage = root?.packageName?.takeIf { it.isNotBlank() }
        val effectivePackage = rootPackage ?: packageName
        val finance = FinanceDenylist.isFinance(effectivePackage, appLabel) ||
            FinanceDenylist.isFinance(packageName, appLabel)
        val secure = secureWindow ||
            flagSecure ||
            SecureWindowDetector.isSecure(if (flagSecure) SecureWindowDetector.FLAG_SECURE else 0, screenshotErrorCode) ||
            finance
        val rawText = collectText(root)
        val redacted = root?.let(Redaction::redactNode)
        val hidden = collectHidden(redacted)
        val visible = redacted?.let(::stripHidden)
        val injectionDetected = InjectionGuard.isInstructional(rawText) || hidden.any(InjectionGuard::isInstructional)
        val formatted = IndexedTreeFormatter(screenWidth, screenHeight).format(
            visible,
            IndexedTreeFormatter.PhoneState(
                packageName = effectivePackage,
                appLabel = appLabel,
                focusedText = focusedText(visible),
                keyboardVisible = containsEditable(visible)
            )
        )
        val passwordVisible = containsSecret(redacted)
        val attachScreenshot = visionRequested &&
            captureVerifiedSafe &&
            !ScreenshotPolicy.preferTree(formatted.elements.isNotEmpty()) &&
            ScreenshotPolicy.allowCapture(secure, finance, passwordVisible)
        return PhoneObservation(
            packageName = effectivePackage,
            appLabel = appLabel,
            treeText = UntrustedText.wrap("screen", formatted.text),
            elements = formatted.elements,
            focusedText = if (focusedIsPassword(visible)) Redaction.REDACTED else Redaction.redactSecrets(formatted.focusedText),
            secureWindow = secure,
            passwordFieldVisible = passwordVisible,
            hiddenInstructionDetected = hidden.any(InjectionGuard::isInstructional),
            screenshotAttached = attachScreenshot,
            injectionDetected = injectionDetected
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
        if (node.isFocused && Redaction.isSecretField(node)) return true
        return node.children.any(::focusedIsPassword)
    }

    private fun containsEditable(node: UiNode?): Boolean {
        if (node == null) return false
        return node.isEditable || node.children.any(::containsEditable)
    }

    private fun containsSecret(node: UiNode?): Boolean {
        if (node == null) return false
        return Redaction.isSecretField(node) || node.children.any(::containsSecret)
    }

    private fun collectText(node: UiNode?): String {
        if (node == null) return ""
        return buildString {
            append(node.text)
            append(' ')
            append(node.contentDescription)
            append(' ')
            append(node.hintText)
            node.children.forEach { append(collectText(it)) }
        }
    }
}
