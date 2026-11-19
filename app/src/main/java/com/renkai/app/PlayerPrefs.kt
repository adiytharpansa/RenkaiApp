package com.renkai.app

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * Track selector untuk RENKAI:
 * - Kualitas video: adaptif otomatis (ABR bawaan ExoPlayer), dibatasi ukuran layar perangkat.
 * - Subtitle: dipilih otomatis mengikuti bahasa aplikasi, dengan bahasa cadangan.
 */
@UnstableApi
fun buildTrackSelector(context: Context): DefaultTrackSelector {
    val lang = context.resources.configuration.locales[0].language
    val fallback = if (lang == "en") "id" else "en"
    return DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setPreferredTextLanguages(lang, fallback)
            .setSelectUndeterminedTextLanguage(true)
            .setViewportSizeToPhysicalDisplaySize(context, true)
            .build()
    }
}
