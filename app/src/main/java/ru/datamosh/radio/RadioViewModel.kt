package ru.datamosh.radio

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // ---- settings (saved) -------------------------------------------------------------------

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _hideMore = MutableStateFlow(prefs.getBoolean("hide_more", false))
    val hideMore: StateFlow<Boolean> = _hideMore

    private val _hideRecent = MutableStateFlow(prefs.getBoolean("hide_recent", false))
    val hideRecent: StateFlow<Boolean> = _hideRecent

    private val _delay = MutableStateFlow(prefs.getFloat("delay_seconds", 0f))
    val delaySeconds: StateFlow<Float> = _delay

    fun setDelay(v: Float) {
        _delay.value = v
        RadioRepo.delaySeconds = v
        prefs.edit().putFloat("delay_seconds", v).apply()
        AppLog.d("UI", "Setting 'track change delay' = $v s")
    }

    fun setHideMore(v: Boolean) {
        _hideMore.value = v
        prefs.edit().putBoolean("hide_more", v).apply()
        AppLog.d("UI", "Setting 'hide More from DATAMOSH' = $v")
    }

    fun setHideRecent(v: Boolean) {
        _hideRecent.value = v
        prefs.edit().putBoolean("hide_recent", v).apply()
        AppLog.d("UI", "Setting 'hide recent tracks' = $v")
    }

    // ---- app updates ------------------------------------------------------------------------

    private val _update = MutableStateFlow<UpdateUi?>(null)
    val update: StateFlow<UpdateUi?> = _update

    init {
        checkOnLaunch()
    }

    /** Automatic check on app start; respects "remind later" and "skip this version". */
    private fun checkOnLaunch() {
        viewModelScope.launch {
            if (prefs.getString("update_remind_day", null) == Updates.today()) {
                AppLog.d("UPDATE", "Remind-later is active for today, auto check skipped")
                return@launch
            }
            val result = withContext(Dispatchers.IO) { runCatching { Updates.fetchLatest() } }
            result.onFailure { AppLog.e("UPDATE", "Update check failed", it) }
            val info = result.getOrNull() ?: return@launch
            AppLog.d("UPDATE", "Latest release: ${info.name}, published ${info.publishedAt}")
            if (!Updates.isNewer(info)) {
                AppLog.d("UPDATE", "No newer version")
            } else if (Updates.isSkipped(prefs.getLong("update_skipped_at", 0L), info)) {
                AppLog.d("UPDATE", "Version ${info.name} was skipped by the user")
            } else {
                _update.value = UpdateUi.Available(info)
            }
        }
    }

    /** Manual check from the settings: ignores "remind later" and "skip". */
    fun checkManually() {
        viewModelScope.launch {
            AppLog.d("UPDATE", "Manual update check")
            val result = withContext(Dispatchers.IO) { runCatching { Updates.fetchLatest() } }
            if (result.isFailure) {
                AppLog.e("UPDATE", "Manual update check failed", result.exceptionOrNull())
                Toast.makeText(
                    getApplication<Application>(),
                    "Не удалось проверить обновления. Проверь подключение к интернету",
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            val info = result.getOrNull()
            _update.value = if (info != null && Updates.isNewer(info)) UpdateUi.Available(info) else UpdateUi.UpToDate
        }
    }

    fun remindLater() {
        AppLog.d("UPDATE", "Remind later (until the end of today)")
        prefs.edit().putString("update_remind_day", Updates.today()).apply()
        _update.value = null
    }

    fun skipVersion() {
        val a = _update.value as? UpdateUi.Available ?: return
        AppLog.d("UPDATE", "Version ${a.info.name} skipped")
        prefs.edit().putLong("update_skipped_at", a.info.publishedAt.epochSecond).apply()
        _update.value = null
    }

    fun dismissUpdate() {
        _update.value = null
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
