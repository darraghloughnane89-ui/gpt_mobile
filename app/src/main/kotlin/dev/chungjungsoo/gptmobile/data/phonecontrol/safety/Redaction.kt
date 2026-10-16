package dev.chungjungsoo.gptmobile.data.phonecontrol.safety

import dev.chungjungsoo.gptmobile.data.phonecontrol.UiNode

object Redaction {
    const val REDACTED = "[redacted]"
    private val secretAssignment = Regex("(?i)(password|passwd|secret|api[_ ]?key|token)\\s*[:=]\\s*\\S+")

    fun redactLog(text: String): String = secretAssignment.replace(text) { match ->
        "${match.groupValues[1]}=$REDACTED"
    }

    fun redactNode(node: UiNode): UiNode {
        val redactedChildren = node.children.map(::redactNode)
        if (!node.isPassword) return node.copy(children = redactedChildren)
        return node.copy(
            text = REDACTED,
            contentDescription = "",
            hintText = "",
            children = redactedChildren
        )
    }

    fun treeContainsSecret(treeText: String, secret: String): Boolean {
        if (secret.isBlank()) return false
        return treeText.contains(secret)
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
        val own = if (node.isPassword) {
            listOf(BlackoutRect(node.bounds.left, node.bounds.top, node.bounds.right, node.bounds.bottom))
        } else {
            emptyList()
        }
        return own + node.children.flatMap(::passwordRects)
    }
}
