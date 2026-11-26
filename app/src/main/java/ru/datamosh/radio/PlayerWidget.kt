package ru.datamosh.radio

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.widget.RemoteViews
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Size class of the 3x1 widget; the layout changes when the user resizes it. */
private enum class WidgetSize { S, M, L }

private fun sizeOf(options: Bundle): WidgetSize {
    val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
    return when {
        w < 230 -> WidgetSize.S // narrow: cover + like + play, no text
        w >= 320 -> WidgetSize.L // wide: bigger text and buttons
        else -> WidgetSize.M
    }
}

private const val ACTION_WIDGET_LIKE = "ru.datamosh.radio.WIDGET_LIKE"

/** Shared behaviour of both widgets: refresh on update/resize and the like button. */
open class BasePlayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        AppLog.d("WIDGET", "onUpdate, ${ids.size} widget(s)")
        val pending = goAsync()
        PlayerWidgets.scope.launch {
            try {
                PlayerWidgets.render(context, askApiIfIdle = true)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle,
    ) {
        PlayerWidgets.scope.launch { PlayerWidgets.render(context, askApiIfIdle = false) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_LIKE) {
            val pending = goAsync()
            PlayerWidgets.scope.launch {
                try {
                    PlayerWidgets.toggleLike(context)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}

/** 3x1 widget: cover, DATAMOSH mark, title/artist, like and play/pause. */
class PlayerWidget : BasePlayerWidget()

/** 2x3 widget: the cover takes 2x2, the remaining 2x1 holds the like and play/pause buttons. */
class PlayerWidgetTall : BasePlayerWidget()

object PlayerWidgets {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("widget", Context.MODE_PRIVATE)

    /** While the app process is alive, redraw the widgets whenever the track, likes or play state change. */
    fun start(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch {
            combine(
                RadioRepo.state.map { it.now?.id }.distinctUntilChanged(),
                LikesStore.likes,
                RadioPlayback.playing,
            ) { _, _, _ -> }.collect { render(app, askApiIfIdle = false) }
        }
    }

    private fun saved(ctx: Context): Track? {
        val p = prefs(ctx)
        val id = p.getString("id", null) ?: return null
        return Track(id, p.getString("title", "") ?: "", p.getString("artist", "") ?: "", p.getString("art", "") ?: "")
    }

    private fun save(ctx: Context, t: Track) {
        prefs(ctx).edit()
            .putString("id", t.id).putString("title", t.title)
            .putString("artist", t.artist).putString("art", t.art)
            .apply()
    }

    /** Current track: from the running poller, otherwise one request to the API, otherwise the last saved one. */
    private suspend fun currentTrack(ctx: Context, askApiIfIdle: Boolean): Track? {
        val live = RadioRepo.state.value.now
        if (RadioRepo.polling && live != null) return live
        if (askApiIfIdle || live == null) {
            RadioRepo.fetchNowPlayingOnce()?.let { return it }
        }
        return live ?: saved(ctx)
    }

    suspend fun render(ctx: Context, askApiIfIdle: Boolean) {
        val manager = AppWidgetManager.getInstance(ctx)
        val wideIds = manager.getAppWidgetIds(ComponentName(ctx, PlayerWidget::class.java))
        val tallIds = manager.getAppWidgetIds(ComponentName(ctx, PlayerWidgetTall::class.java))
        if (wideIds.isEmpty() && tallIds.isEmpty()) return

        val track = currentTrack(ctx, askApiIfIdle)
        track?.let { save(ctx, it) }
        val liked = track != null && LikesStore.likes.value.any { it.track.id == track.id }
        val playing = RadioPlayback.playing.value
        val cover = track?.art?.takeIf { it.isNotBlank() }?.let { loadCover(ctx, it) }

        if (wideIds.isNotEmpty()) {
            val bySize = HashMap<WidgetSize, RemoteViews>()
            wideIds.forEach { id ->
                val size = sizeOf(manager.getAppWidgetOptions(id))
                val v = bySize.getOrPut(size) { buildWide(ctx, size, track, liked, playing, cover) }
                manager.updateAppWidget(id, v)
            }
        }
        if (tallIds.isNotEmpty()) {
            val v = buildTall(ctx, track, liked, playing, cover)
            tallIds.forEach { manager.updateAppWidget(it, v) }
        }
    }

    suspend fun toggleLike(ctx: Context) {
        val track = currentTrack(ctx, askApiIfIdle = true) ?: return
        val liked = LikesStore.likes.value.any { it.track.id == track.id }
        AppLog.d("WIDGET", (if (liked) "Unlike" else "Like") + " from the widget: ${track.artist} - ${track.title}")
        if (liked) LikesStore.unlike(track.id) else LikesStore.like(track)
        render(ctx, askApiIfIdle = false)
    }

    private suspend fun loadCover(ctx: Context, url: String): Bitmap? = runCatching {
        val req = ImageRequest.Builder(ctx).data(url).allowHardware(false).size(384).build()
        val bmp = ((ctx.imageLoader.execute(req) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        bmp?.let { rounded(it) }
    }.onFailure { AppLog.e("WIDGET", "Cover load failed", it) }.getOrNull()

    private fun rounded(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.18f, size * 0.18f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, (size - src.width) / 2f, (size - src.height) / 2f, paint)
        return out
    }

    private val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        flags,
    )

    /** Cover, like and play/pause: common to both widgets. */
    private fun bindCommon(ctx: Context, v: RemoteViews, liked: Boolean, playing: Boolean, cover: Bitmap?) {
        if (cover != null) v.setImageViewBitmap(R.id.w_cover, cover) else v.setImageViewResource(R.id.w_cover, R.drawable.logo_apk)
        v.setImageViewResource(R.id.w_like, if (liked) R.drawable.ic_widget_heart_filled else R.drawable.ic_widget_heart)
        v.setImageViewResource(R.id.w_play, if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)

        v.setOnClickPendingIntent(R.id.w_cover, openApp(ctx))
        v.setOnClickPendingIntent(
            R.id.w_like,
            PendingIntent.getBroadcast(ctx, 1, Intent(ctx, PlayerWidget::class.java).setAction(ACTION_WIDGET_LIKE), flags),
        )
        // A media button event straight to the service, exactly like the Media3 notification does it.
        val playPause = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setClass(ctx, RadioService::class.java)
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        v.setOnClickPendingIntent(R.id.w_play, PendingIntent.getForegroundService(ctx, 2, playPause, flags))
    }

    private fun buildWide(
        ctx: Context,
        size: WidgetSize,
        track: Track?,
        liked: Boolean,
        playing: Boolean,
        cover: Bitmap?,
    ): RemoteViews {
        val layout = when (size) {
            WidgetSize.S -> R.layout.widget_player_s
            WidgetSize.M -> R.layout.widget_player
            WidgetSize.L -> R.layout.widget_player_l
        }
        val v = RemoteViews(ctx.packageName, layout)
        if (size != WidgetSize.S) {
            v.setTextViewText(R.id.w_title, track?.title?.ifBlank { null } ?: "DATAMOSH FM")
            v.setTextViewText(R.id.w_artist, track?.artist ?: "")
            v.setOnClickPendingIntent(R.id.w_text, openApp(ctx))
        }
        bindCommon(ctx, v, liked, playing, cover)
        return v
    }

    private fun buildTall(ctx: Context, track: Track?, liked: Boolean, playing: Boolean, cover: Bitmap?): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_player_tall)
        v.setTextViewText(R.id.w_title, track?.title?.ifBlank { null } ?: "DATAMOSH FM")
        v.setTextViewText(R.id.w_artist, track?.artist ?: "")
        bindCommon(ctx, v, liked, playing, cover)
        v.setOnClickPendingIntent(R.id.w_text, openApp(ctx))
        return v
    }
}
