package dev.chungjungsoo.gptmobile.data.phonecontrol

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.phonecontrol.plan.PhoneActionKind
import dev.chungjungsoo.gptmobile.data.phonecontrol.plan.PlannedAction
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.UntrustedText
import dev.chungjungsoo.gptmobile.presentation.phonecontrol.RobinAccessibilityService
import dev.chungjungsoo.gptmobile.presentation.phonecontrol.RobinNotificationListenerService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SanitizedNotification(
    val key: String,
    val packageName: String,
    val text: String
)

@Singleton
class PhoneControlEngine @Inject constructor(
    @ApplicationContext context: Context
) {
    private val launcher = StructuredActionLauncher(context)
    private var service: RobinAccessibilityService? = null
    private var notifications: RobinNotificationListenerService? = null

    var foregroundPackage: String = ""
        private set
    var foregroundLabel: String = ""
        private set

    private val _stopped = MutableStateFlow(true)
    val stopped: StateFlow<Boolean> = _stopped.asStateFlow()

    private val _overlayVisible = MutableStateFlow(false)
    val overlayVisible: StateFlow<Boolean> = _overlayVisible.asStateFlow()

    private val inbox = mutableListOf<SanitizedNotification>()

    fun attach(accessibilityService: RobinAccessibilityService) {
        service = accessibilityService
    }

    fun detach(accessibilityService: RobinAccessibilityService) {
        if (service == accessibilityService) service = null
    }

    fun attachNotifications(listener: RobinNotificationListenerService) {
        notifications = listener
    }

    fun detachNotifications(listener: RobinNotificationListenerService) {
        if (notifications == listener) notifications = null
    }

    fun isAttached(): Boolean = service != null

    fun onForeground(packageName: String) {
        if (packageName.isNotBlank()) foregroundPackage = packageName
    }

    fun setForegroundLabel(label: String) {
        foregroundLabel = label
    }

    fun begin() {
        _stopped.value = false
        service?.showOverlay(warning = false)
        _overlayVisible.value = service != null
    }

    fun stop() {
        _stopped.value = true
        service?.hideOverlay()
        _overlayVisible.value = false
    }

    fun emphasizeStop() {
        service?.showOverlay(warning = true)
        _overlayVisible.value = true
    }

    fun observe(screenWidth: Int, screenHeight: Int, visionRequested: Boolean): PhoneObservation? =
        service?.observe(screenWidth, screenHeight, visionRequested)

    fun notificationsForModel(): String = inbox.joinToString("\n") { item ->
        UntrustedText.wrap("notification", "${item.packageName}: ${item.text}")
    }

    fun acceptNotification(key: String, packageName: String, rawText: String) {
        val wrapped = UntrustedText.wrap("notification", rawText)
        inbox.add(0, SanitizedNotification(key, packageName, wrapped))
        if (inbox.size > 20) inbox.removeAt(inbox.lastIndex)
    }

    fun perform(action: PlannedAction, elements: List<dev.chungjungsoo.gptmobile.data.phonecontrol.IndexedElement>): Boolean {
        val accessibility = service ?: return false
        return when (action.kind) {
            PhoneActionKind.LAUNCH_APP -> launcher.launchApp(action.packageName ?: action.text.orEmpty())
            PhoneActionKind.OPEN_INTENT -> launcher.launchView(action.text.orEmpty())
            PhoneActionKind.APP_FUNCTION -> launcher.executeAppFunction(action.packageName.orEmpty(), action.text.orEmpty()) ||
                launcher.launchApp(action.packageName.orEmpty())
            PhoneActionKind.BACK -> accessibility.pressBack()
            PhoneActionKind.HOME -> accessibility.pressHome()
            PhoneActionKind.TAP -> tapElement(accessibility, action, elements)
            PhoneActionKind.TYPE -> typeElement(accessibility, action, elements)
            PhoneActionKind.SWIPE, PhoneActionKind.SCROLL -> accessibility.swipe(
                action.x ?: 500,
                action.y ?: 1600,
                action.x2 ?: action.x ?: 500,
                action.y2 ?: (action.y ?: 1600) - 600
            )
            PhoneActionKind.REQUEST_AUTOFILL -> false
            PhoneActionKind.REPLY_NOTIFICATION -> notifications?.reply(action.text.orEmpty()) == true
            PhoneActionKind.WAIT, PhoneActionKind.COMPLETE -> true
        }
    }

    private fun tapElement(
        accessibility: RobinAccessibilityService,
        action: PlannedAction,
        elements: List<IndexedElement>
    ): Boolean {
        val element = action.index?.let { index -> elements.firstOrNull { it.index == index } }
        val bounds = element?.bounds?.let(UiBounds::parse)
        val point = bounds?.center()
        val x = point?.first ?: action.x ?: return false
        val y = point?.second ?: action.y ?: return false
        return accessibility.tap(x, y)
    }

    private fun typeElement(
        accessibility: RobinAccessibilityService,
        action: PlannedAction,
        elements: List<IndexedElement>
    ): Boolean {
        val element = action.index?.let { index -> elements.firstOrNull { it.index == index } } ?: return false
        if (element.password) return false
        return accessibility.type(UiBounds.parse(element.bounds), action.text.orEmpty())
    }
}
