package dev.chungjungsoo.gptmobile.data.phonecontrol

data class UiBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int
        get() = right - left

    val height: Int
        get() = bottom - top

    fun toCsv(): String = "$left,$top,$right,$bottom"

    fun center(): Pair<Int, Int> = (left + right) / 2 to (top + bottom) / 2

    companion object {
        fun parse(csv: String): UiBounds {
            val parts = csv.split(",").map { it.trim().toInt() }
            require(parts.size == 4) { "Invalid bounds: $csv" }
            return UiBounds(parts[0], parts[1], parts[2], parts[3])
        }
    }
}

data class UiNode(
    val className: String = "",
    val resourceId: String = "",
    val text: String = "",
    val contentDescription: String = "",
    val hintText: String = "",
    val bounds: UiBounds = UiBounds(0, 0, 0, 0),
    val isClickable: Boolean = false,
    val isLongClickable: Boolean = false,
    val isCheckable: Boolean = false,
    val isChecked: Boolean = false,
    val isEditable: Boolean = false,
    val isPassword: Boolean = false,
    val isFocused: Boolean = false,
    val isVisibleToUser: Boolean = true,
    val drawingOrder: Int? = null,
    val windowId: Int? = null,
    val opacity: Float = 1f,
    val children: List<UiNode> = emptyList()
) {
    val lowConfidence: Boolean
        get() = opacity < LOW_OPACITY || !isVisibleToUser

    companion object {
        const val LOW_OPACITY = 0.3f
    }
}

data class IndexedElement(
    val index: Int,
    val resourceId: String,
    val className: String,
    val checkedState: String,
    val text: String,
    val bounds: String,
    val displayBounds: String? = null,
    val tapBlockers: List<String> = emptyList(),
    val lowConfidence: Boolean = false,
    val password: Boolean = false,
    val children: List<IndexedElement> = emptyList()
)

data class PhoneObservation(
    val packageName: String,
    val appLabel: String,
    val treeText: String,
    val elements: List<IndexedElement>,
    val focusedText: String,
    val secureWindow: Boolean,
    val passwordFieldVisible: Boolean,
    val hiddenInstructionDetected: Boolean,
    val screenshotAttached: Boolean
)
