package com.renkai.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

val RenkaiYellow = Color(0xFFFFD60A)

private val DarkColors = darkColorScheme(
    primary = RenkaiYellow,
    onPrimary = Color(0xFF1A1500),
    primaryContainer = Color(0xFF3D3200),
    onPrimaryContainer = Color(0xFFFFE680),
    background = Color(0xFF0E0E0F),
    onBackground = Color(0xFFEDEAE0),
    surface = Color(0xFF161618),
    onSurface = Color(0xFFEDEAE0),
    surfaceVariant = Color(0xFF222225),
    onSurfaceVariant = Color(0xFFC9C6B8),
    outline = Color(0xFF48453A),
)

private val LightColors = lightColorScheme(
    primary = RenkaiYellow,
    onPrimary = Color(0xFF1A1500),
    primaryContainer = Color(0xFFFFF0B3),
    onPrimaryContainer = Color(0xFF241A00),
    background = Color(0xFFFFFCF2),
    onBackground = Color(0xFF1C1B16),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B16),
    surfaceVariant = Color(0xFFF6EFD6),
    onSurfaceVariant = Color(0xFF4D4733),
    outline = Color(0xFFCFC6A0),
)

/**
 * Terang/gelap otomatis mengikuti sistem, aksen kuning.
 *
 * Isi dibungkus Surface supaya warna teks/ikon bawaan (LocalContentColor)
 * mengikuti tema. Tanpa ini teks yang tidak diberi warna jadi hitam di atas
 * latar gelap (judul, "Episode lainnya", tombol kembali, layar muat/galat).
 */
@Composable
fun RenkaiTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}
