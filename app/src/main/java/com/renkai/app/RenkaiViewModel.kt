package com.renkai.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.renkai.app.data.CatalogApi
import com.renkai.app.data.Episode
import com.renkai.app.data.Title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayTarget(val title: Title, val episode: Episode)

data class UiState(
    val catalog: List<Title>? = null,
    val loadError: String? = null,
    val playing: PlayTarget? = null,
    val playbackError: String? = null,
    val favoriteIds: List<String> = emptyList(),
)

@OptIn(UnstableApi::class)
class RenkaiViewModel(app: Application) : AndroidViewModel(app) {
    private val store = UserStore(app)
    private val gate = PlaybackRequestGate()
    private val exoPlayer = ExoPlayer.Builder(app)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(
                    app,
                    CdnFallbackDataSourceFactory(DefaultHttpDataSource.Factory()),
                ),
            ),
        )
        .setTrackSelector(buildTrackSelector(app))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _player = MutableStateFlow<Player?>(exoPlayer)
    /** Player dimiliki ViewModel dan dilepas saat pemilik layar berakhir. */
    val player: StateFlow<Player?> = _player.asStateFlow()

    private var positions: Map<String, Long> = emptyMap()
    private val positionsReady = CompletableDeferred<Unit>()
    private var autoDone = false

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(playbackError = "Gagal memutar: ${error.errorCodeName}") }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) _state.update { it.copy(playbackError = null) }
            if (playbackState == Player.STATE_ENDED) onEpisodeEnded()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveProgress()
        }
    }

    init {
        exoPlayer.addListener(playerListener)
        loadCatalog()
        viewModelScope.launch {
            store.favorites.collect { ids -> _state.update { it.copy(favoriteIds = ids) } }
        }
        viewModelScope.launch {
            try {
                store.positions.collect {
                    positions = it
                    positionsReady.complete(Unit)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                positions = emptyMap()
                positionsReady.complete(Unit)
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(10_000)
                if (_player.value?.isPlaying == true) saveProgress()
            }
        }
    }

    fun loadCatalog(force: Boolean = false) {
        viewModelScope.launch {
            try {
                val list = CatalogApi.fetchCatalog(force)
                _state.update { it.copy(catalog = list, loadError = null) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loadError = CatalogApi.lastError ?: (e.message ?: "Gagal memuat katalog"))
                }
            }
        }
    }

    fun autoPlayOnce(titleQuery: String?, episodeNumber: Int) {
        val list = _state.value.catalog ?: return
        if (titleQuery == null || autoDone) return
        autoDone = true
        val t = list.firstOrNull { it.id == titleQuery || it.title.contains(titleQuery, ignoreCase = true) }
        val ep = t?.episodes?.firstOrNull { it.number == episodeNumber } ?: t?.episodes?.firstOrNull()
        if (t != null && ep != null) play(t, ep)
    }

    fun play(t: Title, ep: Episode) {
        saveProgress() // simpan posisi episode sebelumnya
        val requestId = gate.issue()
        _state.update { it.copy(playing = PlayTarget(t, ep), playbackError = null) }
        viewModelScope.launch {
            try {
                positionsReady.await()
                val url = CatalogApi.pickDoor(ep.playlist)
                if (!gate.isCurrent(requestId)) return@launch
                val mime = when {
                    url.contains(".m3u8", true) -> MimeTypes.APPLICATION_M3U8
                    url.contains(".mpd", true) -> MimeTypes.APPLICATION_MPD
                    else -> null
                }
                val key = positionKey(t.id, ep.number)
                val meta = MediaMetadata.Builder()
                    .setTitle("${t.title} · Episode ${ep.number}")
                    .setArtist(t.title)
                    .apply { t.poster?.let { setArtworkUri(Uri.parse(it)) } }
                    .build()
                val item = MediaItem.Builder()
                    .setMediaId(key)
                    .setUri(url)
                    .setMediaMetadata(meta)
                    .apply { mime?.let { setMimeType(it) } }
                    .build()
                exoPlayer.setMediaItem(item, positions[key] ?: 0L) // lanjut dari posisi terakhir
                exoPlayer.prepare()
                exoPlayer.play()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (gate.isCurrent(requestId)) {
                    _state.update {
                        it.copy(playbackError = e.message ?: "Tidak bisa memulai pemutar")
                    }
                }
            }
        }
    }

    fun playLink(url: String) {
        val t = Title(id = customLinkId(url), title = "Tautan kustom", episodes = listOf(Episode(1, url)))
        play(t, t.episodes[0])
    }

    fun closePlayer() {
        gate.invalidate()
        saveProgress()
        _player.value?.run {
            stop()
            clearMediaItems()
        }
        _state.update { it.copy(playing = null, playbackError = null) }
    }

    fun toggleFav(t: Title) {
        if (t.id.startsWith(CUSTOM_LINK_ID_PREFIX)) return
        viewModelScope.launch { store.toggleFavorite(t.id) }
    }

    fun onAppBackgrounded() {
        gate.invalidate()
        saveProgress()
        _player.value?.pause()
    }

    private fun onEpisodeEnded() {
        val cur = _state.value.playing ?: return
        viewModelScope.launch { store.clearPosition(positionKey(cur.title.id, cur.episode.number)) }
        // Putar episode berikutnya otomatis (daftar episode sudah terurut).
        val next = cur.title.episodes.firstOrNull { it.number > cur.episode.number }
        if (next != null) play(cur.title, next)
    }

    private fun saveProgress() {
        val c = _player.value ?: return
        val key = c.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() } ?: return
        val pos = c.currentPosition
        val dur = c.duration
        viewModelScope.launch {
            val nearEnd = dur != C.TIME_UNSET && dur - pos < 30_000L
            if (pos < 5_000L || nearEnd) store.clearPosition(key) else store.setPosition(key, pos)
        }
    }

    override fun onCleared() {
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        super.onCleared()
    }

    companion object {
        private fun positionKey(titleId: String, ep: Int) = "$titleId|$ep"
    }
}
