package dev.chungjungsoo.gptmobile.data.phonecontrol.safety

import dev.chungjungsoo.gptmobile.data.phonecontrol.UiNode

object Redaction {
    const val REDACTED = "[redacted]"
    private val secretAssignment = Regex(
        """(?i)(password|passwd|secret|api[_ ]?key|token)\s*["']?\s*[:=]\s*["']?([^"',\s}]+)"""
    )
    private val skKey = Regex("""\bsk-[A-Za-z0-9_-]{8,}""")

    fun redactSecrets(text: String): String {
        val withoutAssignment = secretAssignment.replace(text) { match ->
            "${match.groupValues[1]}=$REDACTED"
        }
        return skKey.replace(withoutAssignment, REDACTED)
    }

    fun redactLog(text: String): String = redactSecrets(text)

    fun isSecretField(node: UiNode): Boolean {
        if (node.isPassword) return true
        val haystack = "${node.resourceId} ${node.hintText} ${node.className}".lowercase()
        return haystack.contains("password") || haystack.contains("passwd") || haystack.contains("passcode")
    }

    fun redactNode(node: UiNode): UiNode = scrub(node, collectSecrets(node))

    fun treeContainsSecret(treeText: String, secret: String): Boolean {
        if (secret.isBlank()) return false
        return treeText.contains(secret)
    }

    private fun collectSecrets(node: UiNode): Set<String> {
        val secrets = linkedSetOf<String>()
        fun walk(current: UiNode) {
            if (isSecretField(current)) {
                val value = current.text.trim()
                val hint = current.hintText.trim()
                if (value.length >= 3 && !value.equals(hint, ignoreCase = true)) {
                    secrets += value
                }
            }
            current.children.forEach(::walk)
        }
        walk(node)
        return secrets
    }

    private fun scrub(node: UiNode, secrets: Set<String>): UiNode {
        val secretField = isSecretField(node)
        return node.copy(
            text = scrubText(node.text, secrets, secretField),
            contentDescription = scrubText(node.contentDescription, secrets, secretField),
            hintText = scrubText(node.hintText, secrets, secretField && secrets.any { node.hintText.contains(it) }),
            children = node.children.map { scrub(it, secrets) }
        )
    }

    private fun scrubText(value: String, secrets: Set<String>, force: Boolean): String {
        if (value.isBlank()) return value
        if (force) return REDACTED
        if (secrets.any { it.isNotBlank() && value.contains(it) }) return REDACTED
        val redacted = redactSecrets(value)
        return redacted
    }
}

object SecretMemoryPolicy {
    fun admit(text: String): String? {
        val redacted = Redaction.redactSecrets(text)
        if (redacted != text) return null
        return text
    }
}

object ScreenshotPolicy {
    fun allowCapture(secureWindow: Boolean, financeApp: Boolean, passwordFieldVisible: Boolean): Boolean =
        !secureWindow && !financeApp && !passwordFieldVisible

    fun preferTree(treeNonEmpty: Boolean): Boolean = treeNonEmpty
}

data class BlackoutRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

object ScreenshotRedactor {
    fun passwordRects(node: UiNode): List<BlackoutRect> {
        val own = if (Redaction.isSecretField(node)) {
            listOf(BlackoutRect(node.bounds.left, node.bounds.top, node.bounds.right, node.bounds.bottom))
        } else {
            emptyList()
        }
        return own + node.children.flatMap(::passwordRects)
    }
}
