/*
 * Copyright 2019 Jeremy Jamet / Kunzisoft.
 *
 * Derived from KeePassDX StructureParser. Field classification follows its
 * hint, HTML attribute, and Android input-type order. Password values are not
 * copied into the result.
 *
 * KeePassDX is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.chungjungsoo.gptmobile.data.phonecontrol.autofill

import android.text.InputType
import android.view.View

data class AutofillNode(
    val id: String,
    val hints: List<String> = emptyList(),
    val htmlInputType: String? = null,
    val htmlAutocomplete: String? = null,
    val inputType: Int = 0,
    val webDomain: String? = null,
    val className: String = ""
)

data class ParsedAutofillStructure(
    val applicationId: String?,
    val webDomain: String?,
    val usernameId: String?,
    val passwordId: String?
) {
    fun isValid(): Boolean = passwordId != null
}

class AutofillFieldParser {
    fun parse(nodes: List<AutofillNode>, applicationId: String?): ParsedAutofillStructure {
        var webDomain: String? = null
        var usernameId: String? = null
        var passwordId: String? = null
        var usernameCandidate: String? = null
        nodes.forEach { node ->
            if (!node.webDomain.isNullOrBlank()) webDomain = node.webDomain
            if (node.className.contains("webview", ignoreCase = true) && node.hints.isEmpty() && node.htmlInputType == null) {
                return@forEach
            }
            when {
                node.hints.isNotEmpty() -> classifyHint(node, onUsername = { id, replace ->
                    if (replace || passwordId == null) usernameId = id else usernameCandidate = id
                }, onPassword = { id ->
                    if (passwordId != null && usernameCandidate != null) usernameId = usernameCandidate
                    passwordId = id
                }, onUsernameCandidate = { usernameCandidate = it })
                !node.htmlInputType.isNullOrBlank() || !node.htmlAutocomplete.isNullOrBlank() ->
                    classifyHtml(node, onUsername = { usernameId = it }, onCandidate = { usernameCandidate = it }, onPassword = { passwordId = it })
                else -> classifyInputType(node, passwordAlreadySet = passwordId != null, onUsername = { usernameId = it }, onCandidate = { usernameCandidate = it }, onPassword = { passwordId = it })
            }
        }
        if (usernameId == null && passwordId != null) usernameId = usernameCandidate
        return ParsedAutofillStructure(applicationId, webDomain, usernameId, passwordId)
    }

    private fun classifyHint(
        node: AutofillNode,
        onUsername: (String, Boolean) -> Unit,
        onPassword: (String) -> Unit,
        onUsernameCandidate: (String) -> Unit
    ) {
        node.hints.forEach { hint ->
            when {
                hint.contains("2faAppOtp", true) || hint.contains("one-time-code", true) || hint.contains("one-time-password", true) -> Unit
                hint.contains(View.AUTOFILL_HINT_USERNAME, true) ||
                    hint.contains(View.AUTOFILL_HINT_EMAIL_ADDRESS, true) ||
                    hint.contains("email", true) ||
                    hint.contains("login", true) -> onUsername(node.id, false)
                hint.contains(View.AUTOFILL_HINT_PHONE, true) -> onUsernameCandidate(node.id)
                hint.contains(View.AUTOFILL_HINT_PASSWORD, true) -> onPassword(node.id)
            }
        }
    }

    private fun classifyHtml(
        node: AutofillNode,
        onUsername: (String) -> Unit,
        onCandidate: (String) -> Unit,
        onPassword: (String) -> Unit
    ) {
        val type = (node.htmlInputType ?: node.htmlAutocomplete).orEmpty()
        when {
            type.equals("email", true) || type.equals("username", true) || type.contains("login", true) -> onUsername(node.id)
            type.equals("text", true) || type.equals("tel", true) -> onCandidate(node.id)
            type.equals("password", true) -> onPassword(node.id)
        }
    }

    private fun classifyInputType(
        node: AutofillNode,
        passwordAlreadySet: Boolean,
        onUsername: (String) -> Unit,
        onCandidate: (String) -> Unit,
        onPassword: (String) -> Unit
    ) {
        val variation = node.inputType and InputType.TYPE_MASK_VARIATION
        val clazz = node.inputType and InputType.TYPE_MASK_CLASS
        when (clazz) {
            InputType.TYPE_CLASS_TEXT -> when (variation) {
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ->
                    if (!passwordAlreadySet) onUsername(node.id)
                InputType.TYPE_TEXT_VARIATION_NORMAL, InputType.TYPE_TEXT_VARIATION_PERSON_NAME, InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT ->
                    if (!passwordAlreadySet) onCandidate(node.id)
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> if (!passwordAlreadySet) onCandidate(node.id) else onPassword(node.id)
                InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> onPassword(node.id)
            }
            InputType.TYPE_CLASS_NUMBER -> if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) onPassword(node.id)
        }
    }
}
