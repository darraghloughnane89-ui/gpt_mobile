package dev.chungjungsoo.gptmobile.data.phonecontrol.safety

object SecureWindowDetector {
    const val FLAG_SECURE = 0x00002000
    const val ERROR_TAKE_SCREENSHOT_SECURE_WINDOW = 6

    fun isSecure(windowFlags: Int, screenshotErrorCode: Int?): Boolean =
        windowFlags and FLAG_SECURE != 0 || screenshotErrorCode == ERROR_TAKE_SCREENSHOT_SECURE_WINDOW
}
