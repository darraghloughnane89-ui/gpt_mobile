package dev.chungjungsoo.gptmobile.presentation.phonecontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dagger.hilt.android.AndroidEntryPoint
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.phonecontrol.AccessibilityTreeReader
import dev.chungjungsoo.gptmobile.data.phonecontrol.PhoneControlEngine
import dev.chungjungsoo.gptmobile.data.phonecontrol.PhoneObservation
import dev.chungjungsoo.gptmobile.data.phonecontrol.PhoneObservationBuilder
import dev.chungjungsoo.gptmobile.data.phonecontrol.UiBounds
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.FinanceDenylist
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.ScreenshotPolicy
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.SecureWindowDetector
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.StopOverlaySpec
import javax.inject.Inject

@AndroidEntryPoint
class RobinAccessibilityService : AccessibilityService() {
    @Inject
    lateinit var engine: PhoneControlEngine

    private var overlay: LinearLayout? = null
    private var secureFailure: Boolean = false
    private var screenshotErrorCode: Int? = null
    private var captureVerifiedSafe: Boolean = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        engine.onForeground(event?.packageName?.toString().orEmpty())
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideOverlay()
        engine.detach(this)
        super.onDestroy()
    }

    fun observe(screenWidth: Int, screenHeight: Int, visionRequested: Boolean): PhoneObservation? {
        val root = rootInActiveWindow ?: return null
        val rootPackage = root.packageName?.toString().orEmpty()
        val node = AccessibilityTreeReader.read(root)
        root.recycle()
        val packageName = rootPackage.ifBlank { engine.foregroundPackage }
        val secure = secureFailure || SecureWindowDetector.isSecure(0, screenshotErrorCode)
        return PhoneObservationBuilder.build(
            root = node,
            packageName = packageName,
            appLabel = engine.foregroundLabel,
            secureWindow = secure || FinanceDenylist.isFinance(packageName, engine.foregroundLabel),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            visionRequested = visionRequested,
            flagSecure = secure,
            screenshotErrorCode = screenshotErrorCode,
            captureVerifiedSafe = captureVerifiedSafe && !secure
        )
    }

    fun tap(x: Int, y: Int): Boolean = gesture(x.toFloat(), y.toFloat(), x.toFloat(), y.toFloat(), 50)

    fun swipe(x: Int, y: Int, x2: Int, y2: Int): Boolean = gesture(x.toFloat(), y.toFloat(), x2.toFloat(), y2.toFloat(), 300)

    fun type(bounds: UiBounds, text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findAt(root, bounds)
        if (node == null) {
            root.recycle()
            return false
        }
        if (node.isPassword) {
            node.recycle()
            root.recycle()
            return false
        }
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        val typed = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        node.recycle()
        root.recycle()
        return typed
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun captureAllowed(observation: PhoneObservation): Boolean = ScreenshotPolicy.allowCapture(
        observation.secureWindow,
        FinanceDenylist.isFinance(observation.packageName, observation.appLabel),
        observation.passwordFieldVisible
    )

    fun noteSecureCaptureFailure() {
        secureFailure = true
        captureVerifiedSafe = false
    }

    fun noteScreenshotResult(success: Boolean, errorCode: Int?) {
        if (SecureWindowDetector.isSecure(0, errorCode)) {
            secureFailure = true
            captureVerifiedSafe = false
            screenshotErrorCode = errorCode
            return
        }
        if (success) {
            captureVerifiedSafe = true
            screenshotErrorCode = null
        }
    }

    fun showOverlay(warning: Boolean): Boolean {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        return try {
            if (overlay == null) {
                val view = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(24, 12, 24, 12)
                    val label = TextView(this@RobinAccessibilityService).apply {
                        setText(R.string.phone_control_overlay)
                    }
                    val stop = Button(this@RobinAccessibilityService).apply {
                        setText(R.string.phone_control_stop)
                        isClickable = true
                        setOnClickListener { engine.stop() }
                    }
                    addView(label)
                    addView(stop)
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    StopOverlaySpec.layoutFlags(),
                    PixelFormat.TRANSLUCENT
                )
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                windowManager.addView(view, params)
                overlay = view
            }
            overlay?.alpha = if (warning) 1f else 0.92f
            overlay != null
        } catch (_: RuntimeException) {
            overlay = null
            false
        }
    }

    fun hideOverlay() {
        val view = overlay ?: return
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        runCatching { windowManager.removeView(view) }
        overlay = null
    }

    private fun gesture(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            if (x1 != x2 || y1 != y2) lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun findAt(node: AccessibilityNodeInfo, bounds: UiBounds): AccessibilityNodeInfo? {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val matches = rect.left == bounds.left && rect.top == bounds.top && rect.right == bounds.right && rect.bottom == bounds.bottom
        if (matches) return AccessibilityNodeInfo.obtain(node)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = findAt(child, bounds)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    fun openSettings() {
        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
