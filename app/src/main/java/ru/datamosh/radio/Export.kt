package ru.datamosh.radio

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Saves text files into the public Download folder. */
object Downloads {
    /** Returns true on success. An existing file with the same name (made by this app) is overwritten. */
    fun saveText(ctx: Context, name: String, text: String): Boolean = runCatching {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = ctx.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

            val existing = resolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                arrayOf(name, Environment.DIRECTORY_DOWNLOADS + "%"),
                null,
            )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null }

            val rewritten = existing?.let { uri ->
                runCatching {
                    resolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                }.isSuccess
            } ?: false

            if (!rewritten) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
                resolver.openOutputStream(uri)!!.use { it.write(bytes) }
            }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            File(dir, name).writeBytes(bytes)
        }
        true
    }.onFailure { AppLog.e("EXPORT", "Failed to save $name", it) }.getOrDefault(false)
}
