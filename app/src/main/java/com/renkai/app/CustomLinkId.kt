package com.renkai.app

import java.security.MessageDigest

internal const val CUSTOM_LINK_ID_PREFIX = "tautan-kustom:"

internal fun customLinkId(url: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
    val hex = "0123456789abcdef"
    return buildString(CUSTOM_LINK_ID_PREFIX.length + digest.size * 2) {
        append(CUSTOM_LINK_ID_PREFIX)
        digest.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(hex[value ushr 4])
            append(hex[value and 0x0f])
        }
    }
}
