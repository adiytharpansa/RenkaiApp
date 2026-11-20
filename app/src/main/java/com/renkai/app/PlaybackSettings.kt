package com.renkai.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import java.util.Locale

/** Satu pilihan resolusi (varian HLS/DASH dengan bitrate tertinggi untuk tinggi tsb). */
data class QualityOption(val height: Int, val bitrate: Int, val group: Tracks.Group, val index: Int)

/** Satu trek subtitle yang tersedia di manifest. */
data class SubtitleOption(val label: String, val language: String?, val group: Tracks.Group, val index: Int)

data class PlaybackTracks(
    val qualities: List<QualityOption> = emptyList(),
    /** null = mode Otomatis (ABR); selain itu resolusi yang dikunci pengguna. */
    val manualHeight: Int? = null,
    /** Resolusi yang sedang benar-benar diputar. */
    val currentHeight: Int? = null,
    val subtitles: List<SubtitleOption> = emptyList(),
    val selectedSubtitle: SubtitleOption? = null,
)

private fun languageLabel(f: Format): String {
    val lang = f.language
    if (!lang.isNullOrBlank() && lang != C.LANGUAGE_UNDETERMINED) {
        val name = Locale.forLanguageTag(lang)
            .getDisplayLanguage(Locale.forLanguageTag(AppLanguage.DEFAULT))
        if (name.isNotBlank() && !name.equals(lang, ignoreCase = true)) {
            return name.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }
    return f.label ?: lang ?: "Subtitle"
}

fun snapshotTracks(player: Player): PlaybackTracks {
    val tracks = player.currentTracks
    val params = player.trackSelectionParameters
    val bestByHeight = HashMap<Int, QualityOption>()
    var playingHeight: Int? = null
    val subs = ArrayList<SubtitleOption>()
    var selectedSub: SubtitleOption? = null

    for (g in tracks.groups) {
        when (g.type) {
            C.TRACK_TYPE_VIDEO -> for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                val f = g.getTrackFormat(i)
                if (f.height <= 0) continue
                if (g.isTrackSelected(i)) playingHeight = f.height
                val cur = bestByHeight[f.height]
                if (cur == null || f.bitrate > cur.bitrate) {
                    bestByHeight[f.height] = QualityOption(f.height, f.bitrate, g, i)
                }
            }
            C.TRACK_TYPE_TEXT -> for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                val f = g.getTrackFormat(i)
                val opt = SubtitleOption(
                    label = languageLabel(f),
                    language = f.language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED },
                    group = g, index = i,
                )
                subs.add(opt)
                if (g.isTrackSelected(i)) selectedSub = opt
            }
        }
    }
    val manual = params.overrides.values.any { it.type == C.TRACK_TYPE_VIDEO }
    val textOff = C.TRACK_TYPE_TEXT in params.disabledTrackTypes
    return PlaybackTracks(
        qualities = bestByHeight.values.sortedByDescending { it.height },
        manualHeight = if (manual) playingHeight else null,
        currentHeight = playingHeight,
        subtitles = subs,
        selectedSubtitle = if (textOff) null else selectedSub,
    )
}

@Composable
fun rememberPlaybackTracks(player: Player): State<PlaybackTracks> {
    val state = remember(player) { mutableStateOf(snapshotTracks(player)) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { state.value = snapshotTracks(player) }
            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                state.value = snapshotTracks(player)
            }
        }
        player.addListener(l)
        state.value = snapshotTracks(player)
        onDispose { player.removeListener(l) }
    }
    return state
}

/** Kembali ke kualitas otomatis (ABR). */
fun Player.useAutoQuality() {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        .build()
}

/** Kunci ke satu resolusi. */
fun Player.useQuality(o: QualityOption) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index))
        .build()
}

/** Pilih subtitle; null = matikan. Bahasa pilihan diingat untuk episode berikutnya. */
fun Player.useSubtitle(o: SubtitleOption?) {
    val b = trackSelectionParameters.buildUpon()
    if (o == null) {
        b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    } else {
        b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        b.setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index))
        o.language?.let { lang ->
            val langs = (listOf(lang) + trackSelectionParameters.preferredTextLanguages).distinct()
            b.setPreferredTextLanguages(*langs.toTypedArray())
        }
    }
    trackSelectionParameters = b.build()
}

@Composable
fun PlaybackSettingsButton(player: Player, modifier: Modifier = Modifier, compact: Boolean = false) {
    val t by rememberPlaybackTracks(player)
    var open by remember { mutableStateOf(false) }

    val qLabel = when {
        t.manualHeight != null -> "${t.manualHeight}p"
        t.currentHeight != null -> "Otomatis (${t.currentHeight}p)"
        else -> "Otomatis"
    }
    val sLabel = t.selectedSubtitle?.label ?: "Mati"

    if (compact) {
        Row(
            modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                .clickable { open = true }.padding(10.dp),
        ) { Icon(Icons.Rounded.Settings, "Kualitas & subtitle", tint = Color.White) }
    } else {
        OutlinedButton(
            onClick = { open = true }, modifier = modifier.height(48.dp),
            shape = RoundedCornerShape(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        ) {
            Icon(Icons.Rounded.Settings, null, Modifier.size(18.dp))
            Text(
                "  " + (if (t.qualities.isNotEmpty()) "Kualitas: $qLabel · " else "") + "Subtitle: $sLabel",
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
            )
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Tutup") } },
            title = { Text("Pengaturan pemutar", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (t.qualities.isNotEmpty()) {
                        SectionLabel("Kualitas video")
                        OptionRow(
                            if (t.manualHeight == null && t.currentHeight != null) "Otomatis (${t.currentHeight}p)" else "Otomatis",
                            selected = t.manualHeight == null,
                        ) { player.useAutoQuality(); open = false }
                        for (q in t.qualities) {
                            OptionRow("${q.height}p", selected = t.manualHeight == q.height) {
                                player.useQuality(q); open = false
                            }
                        }
                    }
                    SectionLabel("Subtitle")
                    if (t.subtitles.isEmpty()) {
                        Text(
                            "Tidak tersedia untuk video ini.",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        OptionRow("Mati", selected = t.selectedSubtitle == null) { player.useSubtitle(null); open = false }
                        for (s in t.subtitles) {
                            OptionRow(s.label, selected = t.selectedSubtitle == s) { player.useSubtitle(s); open = false }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 8.dp, top = 8.dp, bottom = 8.dp))
        Text(label, fontSize = 15.sp)
    }
}
