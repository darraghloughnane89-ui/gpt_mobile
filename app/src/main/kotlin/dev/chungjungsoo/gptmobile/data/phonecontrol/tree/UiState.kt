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

class UiState(
    val elements: List<IndexedElement>,
    val screenWidth: Int,
    val screenHeight: Int,
    val useNormalized: Boolean = false
) {
    fun element(index: Int): IndexedElement? = elements.firstOrNull { it.index == index }

    fun elementCenter(index: Int): Pair<Int, Int> {
        val element = element(index) ?: throw IllegalArgumentException("No element found with index $index")
        if (element.bounds.isBlank()) throw IllegalArgumentException("Element $index has no bounds")
        val bounds = UiBounds.parse(element.bounds)
        val center = bounds.center()
        val blockers = element.tapBlockers.map(UiBounds::parse)
        val blocked = blockers.any { it.left <= center.first && center.first < it.right && it.top <= center.second && center.second < it.bottom }
        if (!blocked) return center
        val clipped = UiBounds(
            left = bounds.left.coerceAtLeast(0),
            top = bounds.top.coerceAtLeast(0),
            right = bounds.right.coerceAtMost(screenWidth),
            bottom = bounds.bottom.coerceAtMost(screenHeight)
        )
        return UiGeometry.findUncoveredPoint(clipped, blockers)
            ?: throw IllegalArgumentException("No clear tap point for element $index")
    }

    fun convertPoint(x: Int, y: Int): Pair<Int, Int> {
        if (!useNormalized) return x to y
        val absolute = UiCoordinates.toAbsolute(x, y, screenWidth, screenHeight)
        val absX = if (x == UiCoordinates.NORMALIZED_MAX) (screenWidth - 1).coerceAtLeast(0) else absolute.first
        val absY = if (y == UiCoordinates.NORMALIZED_MAX) (screenHeight - 1).coerceAtLeast(0) else absolute.second
        return absX to absY
    }
}
