package com.renkai.app

/** Menjaga hasil resolusi URL lama agar tidak mengganti pilihan pemutaran terbaru. */
internal class PlaybackRequestGate {
    private var generation = 0L

    fun issue(): Long = ++generation

    fun isCurrent(request: Long): Boolean = request == generation

    fun invalidate() {
        generation++
    }
}
