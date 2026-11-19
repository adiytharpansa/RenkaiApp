package com.renkai.app

/** Mencegah perintah Play dari kontrol sistem memulai audio saat aplikasi di latar belakang. */
internal object PlaybackBackgroundPolicy {
    @Volatile
    var isAppForeground: Boolean = false
        private set

    fun markAppForeground() {
        isAppForeground = true
    }

    fun markAppBackground() {
        isAppForeground = false
    }

    fun shouldRejectPlayPause(appForeground: Boolean, playWhenReady: Boolean): Boolean =
        !appForeground && !playWhenReady
}
