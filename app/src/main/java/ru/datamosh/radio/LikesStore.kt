package ru.datamosh.radio

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class LikedTrack(val track: Track, val coverPath: String?, val likedAt: Long)

/** Liked tracks live in filesDir/likes.json, covers in filesDir/covers/<id>.jpg. */
object LikesStore {
    private lateinit var root: File
    private val mutex = Mutex()
    private val _likes = MutableStateFlow<List<LikedTrack>>(emptyList())
    val likes: StateFlow<List<LikedTrack>> = _likes

    fun init(ctx: Context) {
        root = ctx.filesDir
        val f = File(root, "likes.json")
        if (!f.exists()) return
        runCatching {
            val a = JSONArray(f.readText())
            _likes.value = (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                LikedTrack(
                    Track(o.getString("id"), o.getString("title"), o.getString("artist"), o.optString("art")),
                    o.optString("cover").ifBlank { null },
                    o.optLong("at"),
                )
            }
        }
    }

    suspend fun like(t: Track) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_likes.value.any { it.track.id == t.id }) return@withLock
            val cover = runCatching {
                val dir = File(root, "covers").apply { mkdirs() }
                val f = File(dir, "${t.id}.jpg")
                f.writeBytes(httpGet(t.art))
                f.absolutePath
            }.onFailure { AppLog.e("LIKES", "Cover download failed for ${t.id}", it) }.getOrNull()
            AppLog.d("LIKES", "Liked: ${t.artist} - ${t.title} (cover ${if (cover != null) "saved" else "NOT saved"})")
            _likes.value = listOf(LikedTrack(t, cover, System.currentTimeMillis())) + _likes.value
            save()
        }
    }

    suspend fun unlike(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _likes.value.firstOrNull { it.track.id == id }?.coverPath?.let { File(it).delete() }
            AppLog.d("LIKES", "Unliked: $id")
            _likes.value = _likes.value.filter { it.track.id != id }
            save()
        }
    }

    private fun save() {
        val a = JSONArray()
        _likes.value.forEach {
            a.put(
                JSONObject()
                    .put("id", it.track.id)
                    .put("title", it.track.title)
                    .put("artist", it.track.artist)
                    .put("art", it.track.art)
                    .put("cover", it.coverPath ?: "")
                    .put("at", it.likedAt),
            )
        }
        File(root, "likes.json").writeText(a.toString())
    }
}
