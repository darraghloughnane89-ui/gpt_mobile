/*
 * Adapted from mobilerun (https://github.com/droidrun/mobilerun).
 * Copyright (c) 2025 Niels Schmidt
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package dev.chungjungsoo.gptmobile.data.phonecontrol.tree

import dev.chungjungsoo.gptmobile.data.phonecontrol.IndexedElement
import dev.chungjungsoo.gptmobile.data.phonecontrol.UiBounds
import dev.chungjungsoo.gptmobile.data.phonecontrol.UiNode
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.Redaction

class ConciseTreeFilter(
    private val minElementSize: Int = 5
) {
    fun filter(node: UiNode, screenWidth: Int, screenHeight: Int): UiNode? {
        if (!intersectsScreen(node.bounds, screenWidth, screenHeight)) return null
        if (node.bounds.width <= minElementSize || node.bounds.height <= minElementSize) return null
        val children = node.children.mapNotNull { filter(it, screenWidth, screenHeight) }
        return node.copy(children = children)
    }

    private fun intersectsScreen(bounds: UiBounds, screenWidth: Int, screenHeight: Int): Boolean = !(
        bounds.right <= 0 ||
            bounds.bottom <= 0 ||
            bounds.left >= screenWidth ||
            bounds.top >= screenHeight
        )
}

class IndexedTreeFormatter(
    private val screenWidth: Int? = null,
    private val screenHeight: Int? = null,
    private val useNormalized: Boolean = false
) {
    fun format(root: UiNode?, phoneState: PhoneState): FormattedTree {
        stored.clear()
        val filtered = root?.let {
            ConciseTreeFilter().filter(
                it,
                screenWidth ?: it.bounds.width.coerceAtLeast(1),
                screenHeight ?: it.bounds.height.coerceAtLeast(1)
            )
        }
        if (filtered != null) flatten(filtered, intArrayOf(1))
        val elements = stored.toList()
        val treeText = formatElements(elements)
        return FormattedTree(
            text = phoneStateText(phoneState) + "\n\n" + treeText,
            focusedText = phoneState.focusedText,
            elements = elements
        )
    }

    private fun flatten(node: UiNode, counter: IntArray): List<IndexedElement> {
        val formatted = formatNode(node, counter[0])
        counter[0] += 1
        val results = mutableListOf(formatted)
        val siblings = mutableListOf<Pair<UiNode, IndexedElement>>()
        node.children.forEach { child ->
            val descendants = flatten(child, counter)
            if (descendants.isNotEmpty()) {
                siblings += child to descendants.first()
                results += descendants
            }
        }
        addTapBlockers(siblings)
        return results
    }

    private fun addTapBlockers(siblings: List<Pair<UiNode, IndexedElement>>) {
        siblings.forEach { (raw, formatted) ->
            val order = raw.drawingOrder ?: return@forEach
            val window = raw.windowId ?: return@forEach
            if (window < 0) return@forEach
            val bounds = UiBounds.parse(formatted.bounds)
            val blockers = siblings.mapNotNull { (other, candidate) ->
                val otherOrder = other.drawingOrder
                when {
                    other.windowId != window ||
                        otherOrder == null ||
                        otherOrder <= order ||
                        !other.isVisibleToUser ||
                        !(other.isClickable || other.isLongClickable) -> null
                    UiGeometry.rectsOverlap(bounds, UiBounds.parse(candidate.bounds)) -> candidate.bounds
                    else -> null
                }
            }
            if (blockers.isNotEmpty()) {
                val index = resultsIndex(formatted.index)
                if (index >= 0) {
                    stored[index] = stored[index].copy(tapBlockers = blockers)
                }
            }
        }
    }

    private val stored = mutableListOf<IndexedElement>()

    private fun resultsIndex(index: Int): Int = stored.indexOfFirst { it.index == index }

    private fun formatNode(node: UiNode, index: Int): IndexedElement {
        val bounds = node.bounds.toCsv()
        val display = if (useNormalized && screenWidth != null && screenHeight != null) {
            UiCoordinates.boundsToNormalized(node.bounds, screenWidth, screenHeight)
        } else {
            null
        }
        val visibleText = when {
            Redaction.isSecretField(node) -> REDACTED
            node.lowConfidence -> ""
            else -> node.text.ifBlank { node.contentDescription }.ifBlank { node.resourceId }.ifBlank { node.className }
        }
        val shortClass = node.className.substringAfterLast('.')
        val checked = if (node.isCheckable) {
            if (node.isChecked) "isChecked=True" else "isChecked=False"
        } else {
            ""
        }
        val element = IndexedElement(
            index = index,
            resourceId = node.resourceId,
            className = shortClass,
            checkedState = checked,
            text = visibleText,
            bounds = bounds,
            displayBounds = display,
            lowConfidence = node.lowConfidence,
            password = Redaction.isSecretField(node)
        )
        stored += element
        return element
    }

    private fun formatElements(elements: List<IndexedElement>): String {
        val schema = "'index. className: resourceId, text - bounds(x1,y1,x2,y2)'"
        if (elements.isEmpty()) {
            return "Current Clickable UI elements:\n$schema:\nNo UI elements found"
        }
        val lines = elements.joinToString("\n") { element ->
            val details = listOfNotNull(
                element.resourceId.takeIf { it.isNotBlank() }?.let { "\"$it\"" },
                element.text.takeIf { it.isNotBlank() }?.let { "\"$it\"" }
            ).joinToString(", ")
            val checked = if (element.checkedState.isBlank()) "" else "; ${element.checkedState}"
            val bounds = element.displayBounds ?: element.bounds
            "${element.index}. ${element.className}: $details$checked - ($bounds)"
        }
        return "Current Clickable UI elements:\n$schema:\n$lines"
    }

    private fun phoneStateText(phoneState: PhoneState): String {
        val appLine = when {
            phoneState.appLabel.isNotBlank() && phoneState.packageName.isNotBlank() ->
                "• App: ${phoneState.appLabel} (${phoneState.packageName})"
            phoneState.packageName.isNotBlank() -> "• App: ${phoneState.packageName}"
            else -> ""
        }
        return buildString {
            append("Current Phone State:")
            if (appLine.isNotBlank()) {
                append("\n")
                append(appLine)
            }
            append("\n• Keyboard: ")
            append(if (phoneState.keyboardVisible) "Visible" else "Hidden")
            append("\n• Focused Element: '")
            append(phoneState.focusedText)
            append("'")
        }
    }

    data class PhoneState(
        val packageName: String,
        val appLabel: String,
        val focusedText: String,
        val keyboardVisible: Boolean
    )

    data class FormattedTree(
        val text: String,
        val focusedText: String,
        val elements: List<IndexedElement>
    )

    companion object {
        const val REDACTED = "[redacted]"
    }
}
