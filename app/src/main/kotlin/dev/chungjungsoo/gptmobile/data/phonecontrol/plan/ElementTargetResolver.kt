/*
 * Adapted from minitap-ai/mobile-use.
 * Copyright 2025 Minitap
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.chungjungsoo.gptmobile.data.phonecontrol.plan

import dev.chungjungsoo.gptmobile.data.phonecontrol.IndexedElement
import dev.chungjungsoo.gptmobile.data.phonecontrol.UiBounds

data class ElementTarget(
    val resourceId: String? = null,
    val resourceIdIndex: Int = 0,
    val text: String? = null,
    val textIndex: Int = 0,
    val bounds: UiBounds? = null
)

object ElementTargetResolver {
    fun resolve(elements: List<IndexedElement>, target: ElementTarget): IndexedElement? {
        target.resourceId?.takeIf { it.isNotBlank() }?.let { resourceId ->
            val matches = elements.filter { it.resourceId == resourceId }
            matches.getOrNull(target.resourceIdIndex)?.let { return it }
        }
        target.bounds?.let { bounds ->
            elements.firstOrNull { it.bounds == bounds.toCsv() }?.let { return it }
        }
        target.text?.takeIf { it.isNotBlank() }?.let { text ->
            val matches = elements.filter { it.text == text }
            matches.getOrNull(target.textIndex)?.let { return it }
        }
        return null
    }
}
