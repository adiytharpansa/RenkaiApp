package com.renkai.app

import android.content.Context

/**
 * Bahasa antarmuka aplikasi. Saat ini UI hanya tersedia dalam bahasa Indonesia;
 * bila nanti ditambah terjemahan (mis. values-en), cukup tambahkan kodenya ke
 * [SUPPORTED_UI] dan subtitle otomatis mengikuti.
 */
object AppLanguage {
    private val SUPPORTED_UI = setOf("id")
    const val DEFAULT = "id"

    /** Kode bahasa UI aplikasi saat ini (menghormati bahasa per-aplikasi Android 13+). */
    fun current(context: Context): String {
        val lang = context.resources.configuration.locales.get(0)?.language
        return if (lang != null && lang in SUPPORTED_UI) lang else DEFAULT
    }

    /** Urutan preferensi bahasa subtitle: bahasa aplikasi, lalu Indonesia, lalu Inggris. */
    fun subtitlePreference(context: Context): Array<String> =
        listOf(current(context), "id", "en").distinct().toTypedArray()
}
