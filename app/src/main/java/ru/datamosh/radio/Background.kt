package ru.datamosh.radio

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Downloads the background video once into filesDir and reuses it on later launches. */
suspend fun ensureBackground(ctx: Context): File? = withContext(Dispatchers.IO) {
    val target = File(ctx.filesDir, "bg.webm")
    if (target.exists() && target.length() > 0) {
        AppLog.d("BG", "Using cached background video (${target.length()} bytes)")
        return@withContext target
    }

    val part = File(ctx.filesDir, "bg.webm.part")
    runCatching {
        val c = URL(BACKGROUND_URL).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) DatamoshRadio")
        c.setRequestProperty("Referer", "https://datamosh.ru/")
        try {
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            c.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
        } finally {
            c.disconnect()
        }
        check(part.length() > 0)
        part.renameTo(target)
        AppLog.d("BG", "Background video downloaded: ${target.length()} bytes")
    }.onFailure { AppLog.e("BG", "Background video download failed", it) }
    part.delete()
    target.takeIf { it.exists() && it.length() > 0 }
}
