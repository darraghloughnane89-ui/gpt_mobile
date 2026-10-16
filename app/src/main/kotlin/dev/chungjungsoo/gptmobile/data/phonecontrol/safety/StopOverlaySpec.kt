package dev.chungjungsoo.gptmobile.data.phonecontrol.safety

object StopOverlaySpec {
    const val FLAG_NOT_FOCUSABLE = 0x00000008
    const val FLAG_NOT_TOUCHABLE = 0x00000010
    const val FLAG_NOT_TOUCH_MODAL = 0x00000020
    const val FLAG_LAYOUT_IN_SCREEN = 0x00000100
    const val FLAG_WATCH_OUTSIDE_TOUCH = 0x00040000

    fun layoutFlags(): Int =
        FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or FLAG_LAYOUT_IN_SCREEN or FLAG_WATCH_OUTSIDE_TOUCH

    fun canReceiveStopTap(flags: Int): Boolean {
        if (flags and FLAG_NOT_TOUCHABLE != 0) return false
        if (flags and FLAG_LAYOUT_IN_SCREEN == 0) return false
        if (flags and FLAG_NOT_TOUCH_MODAL == 0) return false
        return true
    }
}
