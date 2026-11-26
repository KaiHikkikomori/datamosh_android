package ru.datamosh.radio

import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import org.json.JSONObject

/**
 * Date and time this build is (or will be) published on GitHub Releases.
 * !!! Edit it before every release build: put the hour in which you press "Publish release"
 * (or a bit later, never earlier).
 *
 * Format: "2026-11-26T13:30" (no time zone = Moscow time), or with any time zone, which the app
 * converts to Moscow time by itself: "2026-11-26T15:30+05:00", "2026-11-26T10:30Z",
 * "2026-11-26T15:30+05:00[Asia/Yekaterinburg]".
 * All comparisons are made in Moscow time and only by the hour (minutes and seconds are ignored).
 */
const val APP_RELEASE_TIME = "2026-11-27T00:00"

/** GitHub repository with the releases ("owner/name"). It must be public. */
const val UPDATE_REPO = "KaiHikkikomori/datamosh_android"

data class ReleaseInfo(val name: String, val publishedAt: Instant, val url: String)

sealed interface UpdateUi {
    data class Available(val info: ReleaseInfo) : UpdateUi
    object UpToDate : UpdateUi
}

object Updates {
    private val MOSCOW: ZoneId = ZoneId.of("Europe/Moscow")

    /** Moscow time, rounded down to the hour. */
    private fun mskHour(t: Instant): Instant =
        ZonedDateTime.ofInstant(t, MOSCOW).truncatedTo(ChronoUnit.HOURS).toInstant()

    /** Reads [APP_RELEASE_TIME]: no zone means Moscow, any other zone is converted. */
    private fun parseReleaseTime(text: String): Instant {
        val t = text.trim().replace(' ', 'T')
        return runCatching { ZonedDateTime.parse(t) }
            .recoverCatching { OffsetDateTime.parse(t).toZonedDateTime() }
            .recoverCatching { LocalDateTime.parse(t).atZone(MOSCOW) }
            .getOrThrow()
            .toInstant()
    }

    private val installed: Instant? by lazy {
        runCatching { parseReleaseTime(APP_RELEASE_TIME) }
            .onFailure { AppLog.e("UPDATE", "APP_RELEASE_TIME is not a valid date: $APP_RELEASE_TIME", it) }
            .getOrNull()
    }

    /** Local calendar day, used by "remind me later". */
    fun today(): String = LocalDate.now().toString()

    /** Latest published release, or null when the repository has no releases. Throws on network errors. */
    fun fetchLatest(): ReleaseInfo? {
        val c = URL("https://api.github.com/repos/$UPDATE_REPO/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "DatamoshFM")
        try {
            val code = c.responseCode
            if (code == 404) return null
            check(code == 200) { "HTTP $code" }
            val j = JSONObject(c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) })
            val name = j.optString("name").ifBlank { j.optString("tag_name") }
            val published = j.optString("published_at")
            if (published.isBlank()) return null
            return ReleaseInfo(name, Instant.parse(published), j.optString("html_url"))
        } finally {
            c.disconnect()
        }
    }

    /** True when the release on GitHub is newer than this build (compared by the hour, Moscow time). */
    fun isNewer(info: ReleaseInfo): Boolean {
        val mine = installed ?: return false
        return mskHour(info.publishedAt) > mskHour(mine)
    }

    /** True when the user chose "skip this version" for this release or for a newer one. */
    fun isSkipped(skippedAtEpoch: Long, info: ReleaseInfo): Boolean =
        skippedAtEpoch > 0 && mskHour(Instant.ofEpochSecond(skippedAtEpoch)) >= mskHour(info.publishedAt)
}
