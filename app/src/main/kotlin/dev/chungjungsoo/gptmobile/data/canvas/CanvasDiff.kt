package dev.chungjungsoo.gptmobile.data.canvas

private const val MAX_DIFF_SIDE = 250

fun diffLines(before: String, after: String): List<CanvasDiffLine> {
    if (before == after) {
        return before.lines().map { line -> CanvasDiffLine(CanvasDiffKind.Same, line) }
    }
    val left = before.lines()
    val right = after.lines()
    var start = 0
    while (start < left.size && start < right.size && left[start] == right[start]) start++
    var leftEnd = left.size
    var rightEnd = right.size
    while (leftEnd > start && rightEnd > start && left[leftEnd - 1] == right[rightEnd - 1]) {
        leftEnd--
        rightEnd--
    }
    val prefix = left.subList(0, start).map { line -> CanvasDiffLine(CanvasDiffKind.Same, line) }
    val suffix = left.subList(leftEnd, left.size).map { line -> CanvasDiffLine(CanvasDiffKind.Same, line) }
    val leftMid = left.subList(start, leftEnd)
    val rightMid = right.subList(start, rightEnd)
    val middle = if (leftMid.size.toLong() * rightMid.size > MAX_DIFF_SIDE.toLong() * MAX_DIFF_SIDE) {
        leftMid.map { line -> CanvasDiffLine(CanvasDiffKind.Removed, line) } +
            rightMid.map { line -> CanvasDiffLine(CanvasDiffKind.Added, line) }
    } else {
        lcsDiff(leftMid, rightMid)
    }
    return prefix + middle + suffix
}

fun spliceSelection(content: String, selection: PendingSelection, replacement: String): String? {
    if (selection.text.isEmpty()) return null
    val start = selection.start.coerceIn(0, content.length)
    val end = selection.end.coerceIn(start, content.length)
    if (content.substring(start, end) == selection.text) {
        return content.substring(0, start) + replacement + content.substring(end)
    }
    val index = content.indexOf(selection.text)
    if (index < 0) return null
    if (content.indexOf(selection.text, index + selection.text.length) >= 0) return null
    return content.substring(0, index) + replacement + content.substring(index + selection.text.length)
}

private fun lcsDiff(left: List<String>, right: List<String>): List<CanvasDiffLine> {
    val rows = left.size
    val columns = right.size
    val scores = Array(rows + 1) { IntArray(columns + 1) }
    for (row in rows - 1 downTo 0) {
        for (column in columns - 1 downTo 0) {
            scores[row][column] = if (left[row] == right[column]) {
                scores[row + 1][column + 1] + 1
            } else {
                maxOf(scores[row + 1][column], scores[row][column + 1])
            }
        }
    }
    val lines = mutableListOf<CanvasDiffLine>()
    var row = 0
    var column = 0
    while (row < rows && column < columns) {
        when {
            left[row] == right[column] -> {
                lines += CanvasDiffLine(CanvasDiffKind.Same, left[row])
                row++
                column++
            }

            scores[row + 1][column] >= scores[row][column + 1] -> {
                lines += CanvasDiffLine(CanvasDiffKind.Removed, left[row])
                row++
            }

            else -> {
                lines += CanvasDiffLine(CanvasDiffKind.Added, right[column])
                column++
            }
        }
    }
    while (row < rows) {
        lines += CanvasDiffLine(CanvasDiffKind.Removed, left[row])
        row++
    }
    while (column < columns) {
        lines += CanvasDiffLine(CanvasDiffKind.Added, right[column])
        column++
    }
    return lines
}
