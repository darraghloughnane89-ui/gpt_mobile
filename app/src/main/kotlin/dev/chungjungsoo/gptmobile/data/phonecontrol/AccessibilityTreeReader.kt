package dev.chungjungsoo.gptmobile.data.phonecontrol

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

object AccessibilityTreeReader {
    fun read(node: AccessibilityNodeInfo?): UiNode? {
        if (node == null) return null
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val children = buildList {
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                read(child)?.let(::add)
                child.recycle()
            }
        }
        return UiNode(
            className = node.className?.toString().orEmpty(),
            resourceId = node.viewIdResourceName.orEmpty(),
            text = node.text?.toString().orEmpty(),
            contentDescription = node.contentDescription?.toString().orEmpty(),
            hintText = node.hintText?.toString().orEmpty(),
            bounds = UiBounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
            isClickable = node.isClickable,
            isLongClickable = node.isLongClickable,
            isCheckable = node.isCheckable,
            isChecked = node.isChecked,
            isEditable = node.isEditable,
            isPassword = node.isPassword,
            isFocused = node.isFocused,
            isVisibleToUser = node.isVisibleToUser,
            drawingOrder = node.drawingOrder,
            windowId = node.windowId,
            opacity = if (node.isVisibleToUser) 1f else 0f,
            packageName = node.packageName?.toString().orEmpty(),
            children = children
        )
    }
}
