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

object UiCoordinates {
    const val NORMALIZED_MAX = 1000

    fun toAbsolute(x: Int, y: Int, width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0) { "Screen dimensions not available." }
        return x * width / NORMALIZED_MAX to y * height / NORMALIZED_MAX
    }

    fun toNormalized(x: Int, y: Int, width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0) { "Screen dimensions not available." }
        return x * NORMALIZED_MAX / width to y * NORMALIZED_MAX / height
    }

    fun boundsToNormalized(bounds: UiBounds, width: Int, height: Int): String {
        val topLeft = toNormalized(bounds.left, bounds.top, width, height)
        val bottomRight = toNormalized(bounds.right, bounds.bottom, width, height)
        return "${topLeft.first},${topLeft.second},${bottomRight.first},${bottomRight.second}"
    }
}
