package ru.datamosh.radio

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class RadioService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        RadioRepo.acquire()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.setMediaItem(item(null))

        // After a pause the buffer is stale: reconnect so playback returns to the live edge.
        val live = object : ForwardingPlayer(player) {
            private var pausedAt = 0L

            override fun play() {
                val stale = pausedAt != 0L && System.currentTimeMillis() - pausedAt > 3_000
                if (wrappedPlayer.playbackState == Player.STATE_IDLE || stale) {
                    wrappedPlayer.stop()
                    wrappedPlayer.prepare()
                }
                pausedAt = 0L
                super.play()
            }

            override fun pause() {
                pausedAt = System.currentTimeMillis()
                super.pause()
            }
        }

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                scope.launch {
                    delay(3_000)
                    if (player.playWhenReady) {
                        player.stop()
                        player.prepare()
                    }
                }
            }
        })

        session = MediaSession.Builder(this, live).build()

        // Same URI + id, only metadata changes: ExoPlayer updates it without interrupting playback.
        scope.launch {
            RadioRepo.state.map { it.now }.distinctUntilChanged().collect { t ->
                if (t != null) player.replaceMediaItem(0, item(t))
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        if (!player.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.release()
        session = null
        player.release()
        RadioRepo.release()
        super.onDestroy()
    }

    private fun item(t: Track?): MediaItem = MediaItem.Builder()
        .setMediaId("radio")
        .setUri(STREAM_URL)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t?.title ?: "DATAMOSH FM")
                .setArtist(t?.artist ?: "")
                .setArtworkUri(t?.art?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
                .build(),
        )
        .build()
}
