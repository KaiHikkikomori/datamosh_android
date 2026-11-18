package ru.datamosh.radio

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RadioViewModel(app: Application) : AndroidViewModel(app) {
    private var controller: MediaController? = null

    private val _wantPlay = MutableStateFlow(false)
    val wantPlay: StateFlow<Boolean> = _wantPlay

    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering

    init {
        val token = SessionToken(app, ComponentName(app, RadioService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            runCatching {
                val c = future.get()
                controller = c
                _wantPlay.value = c.playWhenReady
                _buffering.value = c.playbackState == Player.STATE_BUFFERING
                c.addListener(object : Player.Listener {
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        _wantPlay.value = playWhenReady
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        _buffering.value = state == Player.STATE_BUFFERING
                    }
                })
                AppLog.d("PLAYER", "MediaController connected")
            }.onFailure { AppLog.e("PLAYER", "MediaController connect failed", it) }
        }, ContextCompat.getMainExecutor(app))
    }

    fun toggle() {
        val c = controller
        if (c == null) {
            AppLog.d("UI", "Play/pause tapped, but the player is not connected yet")
            return
        }
        AppLog.d("UI", "Play/pause tapped, wasPlaying=${c.playWhenReady}")
        if (c.playWhenReady) c.pause() else c.play()
    }

    fun toggleLike(t: Track) {
        viewModelScope.launch {
            val liked = LikesStore.likes.value.any { it.track.id == t.id }
            AppLog.d("UI", (if (liked) "Unlike" else "Like") + " tapped: ${t.artist} - ${t.title}")
            if (liked) LikesStore.unlike(t.id) else LikesStore.like(t)
        }
    }

    fun unlike(id: String) {
        AppLog.d("UI", "Unlike tapped in likes list: $id")
        viewModelScope.launch { LikesStore.unlike(id) }
    }

    override fun onCleared() {
        controller?.release()
    }
}
