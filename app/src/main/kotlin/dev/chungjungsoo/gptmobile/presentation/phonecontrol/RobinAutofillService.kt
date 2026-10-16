package dev.chungjungsoo.gptmobile.presentation.phonecontrol

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.phonecontrol.autofill.AutofillFieldParser
import dev.chungjungsoo.gptmobile.data.phonecontrol.autofill.AutofillNode

class RobinAutofillService : android.service.autofill.AutofillService() {
    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }
        val ids = linkedMapOf<String, AutofillId>()
        val nodes = mutableListOf<AutofillNode>()
        var applicationId: String? = null
        for (windowIndex in 0 until structure.windowNodeCount) {
            val window = structure.getWindowNodeAt(windowIndex)
            applicationId = window.title?.toString()?.substringBefore("/") ?: applicationId
            collect(window.rootViewNode, ids, nodes)
        }
        val parsed = AutofillFieldParser().parse(nodes, applicationId)
        val passwordId = parsed.passwordId?.let(ids::get)
        if (passwordId == null) {
            callback.onSuccess(null)
            return
        }
        val intent = Intent(this, AutofillConfirmActivity::class.java).apply {
            putExtra(AutofillConfirmActivity.EXTRA_PASSWORD_ID, passwordId)
            parsed.usernameId?.let(ids::get)?.let { putExtra(AutofillConfirmActivity.EXTRA_USERNAME_ID, it) }
            putExtra(AutofillConfirmActivity.EXTRA_PACKAGE, parsed.applicationId)
            putExtra(AutofillConfirmActivity.EXTRA_DOMAIN, parsed.webDomain)
        }
        val sender = PendingIntent.getActivity(
            this,
            passwordId.hashCode(),
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
        ).intentSender
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1)
        presentation.setTextViewText(android.R.id.text1, getString(R.string.phone_control_autofill_presentation))
        val response = FillResponse.Builder()
            .setAuthentication(arrayOf(passwordId), sender, presentation)
            .build()
        callback.onSuccess(response)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private fun collect(node: AssistStructure.ViewNode, ids: MutableMap<String, AutofillId>, nodes: MutableList<AutofillNode>) {
        val autofillId = node.autofillId
        if (autofillId != null) {
            val id = "f${ids.size}"
            ids[id] = autofillId
            nodes += AutofillNode(
                id = id,
                hints = node.autofillHints?.toList().orEmpty(),
                htmlInputType = node.htmlInfo?.attributes?.firstOrNull { it.first.equals("type", true) }?.second,
                htmlAutocomplete = node.htmlInfo?.attributes?.firstOrNull { it.first.equals("autocomplete", true) }?.second,
                inputType = node.inputType,
                webDomain = node.webDomain,
                className = node.className.orEmpty()
            )
        }
        for (index in 0 until node.childCount) {
            collect(node.getChildAt(index), ids, nodes)
        }
    }
}
