package com.renkai.app

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult

/**
 * Pemutar hidup di layanan agar sesi Media3 stabil; audio dijeda saat app ke background,
 * dan kontrol sistem tidak dapat memulai ulang audio sampai app kembali aktif.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val dataSourceFactory = DefaultDataSource.Factory(
            this,
            CdnFallbackDataSourceFactory(DefaultHttpDataSource.Factory()),
        )
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setTrackSelector(buildTrackSelector(this))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int,
                ): Int {
                    val blockBackgroundPlay = playerCommand == Player.COMMAND_PLAY_PAUSE &&
                        PlaybackBackgroundPolicy.shouldRejectPlayPause(
                            appForeground = PlaybackBackgroundPolicy.isAppForeground,
                            playWhenReady = session.player.playWhenReady,
                        )
                    return if (blockBackgroundPlay) {
                        SessionResult.RESULT_ERROR_NOT_SUPPORTED
                    } else {
                        SessionResult.RESULT_SUCCESS
                    }
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // Aplikasi digeser dari recent apps: hentikan pemutaran dan layanan.
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
