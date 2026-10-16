package dev.chungjungsoo.gptmobile.data.phonecontrol.safety

class ControlSession {
    private var stopped = true
    var overlayVisible: Boolean = false
        private set
    private var listener: (() -> Unit)? = null

    fun begin(overlayShown: Boolean): Boolean {
        if (!overlayShown) {
            stopped = true
            overlayVisible = false
            return false
        }
        stopped = false
        overlayVisible = true
        return true
    }

    fun stop() {
        stopped = true
        overlayVisible = false
        listener?.invoke()
    }

    fun onStop(listener: () -> Unit) {
        this.listener = listener
    }

    fun isStopped(): Boolean = stopped
}
