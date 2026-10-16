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

import dev.chungjungsoo.gptmobile.data.phonecontrol.UiBounds

object UiGeometry {
    fun rectsOverlap(a: UiBounds, b: UiBounds): Boolean = !(
        a.right <= b.left ||
            b.right <= a.left ||
            a.bottom <= b.top ||
            b.bottom <= a.top
        )

    fun findUncoveredPoint(bounds: UiBounds, blockers: List<UiBounds>): Pair<Int, Int>? {
        var regions = if (bounds.left < bounds.right && bounds.top < bounds.bottom) listOf(bounds) else emptyList()
        blockers.forEach { blocker ->
            val remaining = mutableListOf<UiBounds>()
            regions.forEach { region ->
                val overlapLeft = maxOf(region.left, blocker.left)
                val overlapTop = maxOf(region.top, blocker.top)
                val overlapRight = minOf(region.right, blocker.right)
                val overlapBottom = minOf(region.bottom, blocker.bottom)
                if (overlapLeft >= overlapRight || overlapTop >= overlapBottom) {
                    remaining += region
                    return@forEach
                }
                listOf(
                    UiBounds(region.left, region.top, region.right, overlapTop),
                    UiBounds(region.left, overlapBottom, region.right, region.bottom),
                    UiBounds(region.left, overlapTop, overlapLeft, overlapBottom),
                    UiBounds(overlapRight, overlapTop, region.right, overlapBottom)
                ).filter { it.left < it.right && it.top < it.bottom }.forEach { remaining += it }
            }
            regions = remaining
        }
        val best = regions.maxByOrNull { it.width * it.height } ?: return null
        return (best.left + best.right) / 2 to (best.top + best.bottom) / 2
    }

    fun findClearPoint(bounds: UiBounds, blockers: List<UiBounds>, depth: Int = 0): Pair<Int, Int>? {
        val centerX = (bounds.left + bounds.right) / 2
        val centerY = (bounds.top + bounds.bottom) / 2
        val blocked = blockers.any { it.left <= centerX && centerX < it.right && it.top <= centerY && centerY < it.bottom }
        if (!blocked) return centerX to centerY
        if (depth > 4 || bounds.width * bounds.height < 100) return null
        val quadrants = listOf(
            UiBounds(bounds.left, bounds.top, centerX, centerY),
            UiBounds(centerX, bounds.top, bounds.right, centerY),
            UiBounds(bounds.left, centerY, centerX, bounds.bottom),
            UiBounds(centerX, centerY, bounds.right, bounds.bottom)
        )
        var best: Pair<Int, Int>? = null
        var bestArea = 0
        quadrants.forEach { quadrant ->
            val area = quadrant.width * quadrant.height
            if (area <= 0) return@forEach
            val point = findClearPoint(quadrant, blockers, depth + 1) ?: return@forEach
            if (area > bestArea) {
                best = point
                bestArea = area
            }
        }
        return best
    }
}
