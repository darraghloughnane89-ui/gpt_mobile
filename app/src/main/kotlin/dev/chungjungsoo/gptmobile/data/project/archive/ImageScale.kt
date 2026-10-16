package dev.chungjungsoo.gptmobile.data.project.archive

internal object ImageScale {
    fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        val edge = maxOf(width, height)
        if (width <= 0 || height <= 0 || edge <= maxEdge) return 1
        var sample = 1
        while (edge / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    fun targetSize(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
        val edge = maxOf(width, height)
        if (width <= 0 || height <= 0 || edge <= maxEdge) return width to height
        val scale = maxEdge.toFloat() / edge.toFloat()
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }
}
