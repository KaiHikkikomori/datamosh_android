@file:OptIn(UnstableApi::class)

package ru.datamosh.radio

import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun RadioTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(ctx) else darkColorScheme()
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Wavy progress line in the Material 3 Expressive style; the wave flattens when paused. */
@Composable
private fun WavyProgress(progress: Float, playing: Boolean, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "phase",
    )
    val k by animateFloatAsState(if (playing) 1f else 0f, spring(dampingRatio = 0.6f), label = "amp")
    Canvas(modifier.height(20.dp)) {
        val cy = size.height / 2f
        val stroke = 5.dp.toPx()
        val amp = 5.dp.toPx() * k
        val wavelength = 30.dp.toPx()
        val end = size.width * progress.coerceIn(0f, 1f)
        drawLine(track, Offset(end, cy), Offset(size.width, cy), stroke, StrokeCap.Round)
        if (end > 0f) {
            val path = Path()
            path.moveTo(0f, cy + amp * sin(phase))
            var x = 0f
            while (x < end) {
                x = (x + 3f).coerceAtMost(end)
                path.lineTo(x, cy + amp * sin(2f * PI.toFloat() * x / wavelength + phase))
            }
            drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun RadioRoot(vm: RadioViewModel = viewModel()) {
    val state by RadioRepo.state.collectAsStateWithLifecycle()
    RadioTheme { RadioContent(vm, state) }
}

@Composable
private fun RadioContent(vm: RadioViewModel, state: RadioState) {
    val likes by LikesStore.likes.collectAsStateWithLifecycle()
    val wantPlay by vm.wantPlay.collectAsStateWithLifecycle()
    val buffering by vm.buffering.collectAsStateWithLifecycle()
    var showLikes by rememberSaveable { mutableStateOf(false) }
    val likedIds = remember(likes) { likes.map { it.track.id }.toSet() }

    var clean by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current

    // Clean screen: hide the status and navigation bars as well.
    DisposableEffect(clean) {
        val window = (view.context as? Activity)?.window
        val ctrl = window?.let { WindowCompat.getInsetsController(it, view) }
        if (clean) {
            ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { ctrl?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    BackHandler(showLikes || clean) { if (clean) clean = false else showLikes = false }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoBackground()
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0x80000000), Color(0xCC000000))),
                ),
            )
            AnimatedContent(
                targetState = if (clean) 2 else if (showLikes) 1 else 0,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tabs",
            ) { screen ->
                if (screen == 2) {
                    CleanScreen(state) { clean = false }
                } else if (screen == 1) {
                    LikesScreen(likes, onBack = { showLikes = false }, onUnlike = vm::unlike)
                } else {
                    PlayerScreen(
                        state = state,
                        likedIds = likedIds,
                        likesCount = likes.size,
                        wantPlay = wantPlay,
                        buffering = buffering,
                        onToggle = vm::toggle,
                        onLike = vm::toggleLike,
                        onOpenLikes = { showLikes = true },
                        onClean = { clean = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoBackground() {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Local copy first; if the download fails, fall back to streaming from the site.
    var source by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        source = ensureBackground(ctx)?.let { Uri.fromFile(it).toString() } ?: BACKGROUND_URL
    }
    val src = source ?: return

    val exo = remember(src) {
        ExoPlayer.Builder(ctx).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            setMediaItem(MediaItem.fromUri(src))
            prepare()
        }
    }
    DisposableEffect(exo, lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START) exo.play()
            else if (e == Lifecycle.Event.ON_STOP) exo.pause()
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            exo.release()
        }
    }
    // resize_mode="zoom" (see res/layout/bg_player.xml): the video fills the screen height
    // and is centre-cropped on the sides to match the display width.
    AndroidView(
        factory = { c ->
            (LayoutInflater.from(c).inflate(R.layout.bg_player, null) as PlayerView).apply {
                player = exo
            }
        },
        update = { it.player = exo },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun PlayerScreen(
    state: RadioState,
    likedIds: Set<String>,
    likesCount: Int,
    wantPlay: Boolean,
    buffering: Boolean,
    onToggle: () -> Unit,
    onLike: (Track) -> Unit,
    onOpenLikes: () -> Unit,
    onClean: () -> Unit,
) {
    val sys = WindowInsets.systemBars.asPaddingValues()
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = sys.calculateTopPadding() + 8.dp,
            bottom = sys.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { runCatching { uriHandler.openUri("https://t.me/datam0sh") } }) {
                    Icon(
                        painterResource(R.drawable.ic_telegram),
                        contentDescription = "Telegram-канал",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Image(
                        painterResource(R.drawable.logo_fm),
                        contentDescription = "DATAMOSH FM",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(44.dp),
                    )
                }
                FilledTonalIconButton(onClick = onOpenLikes) {
                    Icon(Icons.Rounded.Favorite, contentDescription = "Понравившиеся треки")
                }
            }
        }
        item { NowPlaying(state, state.now?.let { likedIds.contains(it.id) } == true, wantPlay, buffering, onToggle, onLike, onClean) }
        item {
            Text(
                "Недавно играло",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            )
        }
        items(state.history, key = { it.shId }) { h ->
            TrackRow(h.track, null, likedIds.contains(h.track.id)) { onLike(h.track) }
        }
        if (state.history.isEmpty()) {
            item {
                Text(
                    "Список появится, когда доиграет первый трек",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun NowPlaying(
    state: RadioState,
    liked: Boolean,
    wantPlay: Boolean,
    buffering: Boolean,
    onToggle: () -> Unit,
    onLike: (Track) -> Unit,
    onClean: () -> Unit,
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
    }
    val progress = if (state.duration > 0) {
        ((state.elapsed + (nowMs - state.fetchedAt) / 1000f) / state.duration).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(44.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = state.now?.art, label = "cover") { art ->
                if (art.isNullOrBlank()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(64.dp))
                    }
                } else {
                    AsyncImage(
                        model = art,
                        contentDescription = "Обложка",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        AnimatedContent(
            targetState = state.now,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "meta",
        ) { t ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    t?.title ?: if (state.error) "Нет связи с радио" else "Подключаемся…",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    t?.artist ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        WavyProgress(
            progress = progress,
            playing = wantPlay,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            FilledTonalIconButton(onClick = onClean, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.Fullscreen, contentDescription = "Чистый экран")
            }
            val corner by animateDpAsState(
                if (wantPlay) 28.dp else 44.dp,
                spring(),
                label = "playShape",
            )
            FilledIconButton(
                onClick = onToggle,
                modifier = Modifier.size(88.dp),
                shape = RoundedCornerShape(corner),
            ) {
                if (wantPlay && buffering) {
                    CircularProgressIndicator(Modifier.size(40.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(
                        if (wantPlay) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (wantPlay) "Пауза" else "Слушать",
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            FilledTonalIconToggleButton(
                checked = liked,
                onCheckedChange = { state.now?.let(onLike) },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (liked) "Убрать из понравившихся" else "Лайк",
                    tint = if (liked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
        }

        state.next?.let {
            Text(
                "Дальше: ${it.artist} — ${it.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    localCover: String?,
    liked: Boolean,
    extra: @Composable () -> Unit = {},
    onLike: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = localCover?.let { File(it) } ?: track.art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            extra()
            IconToggleButton(checked = liked, onCheckedChange = { onLike() }) {
                Icon(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (liked) "Убрать из понравившихся" else "Лайк",
                    tint = if (liked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
        }
    }
}

@Composable
private fun LikesScreen(likes: List<LikedTrack>, onBack: () -> Unit, onUnlike: (String) -> Unit) {
    val sys = WindowInsets.systemBars.asPaddingValues()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = sys.calculateTopPadding() + 8.dp,
            bottom = sys.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                }
                Text(
                    "Понравившиеся",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (likes.isEmpty()) {
            item {
                Text(
                    "Здесь пока пусто. Нажми сердечко у трека, чтобы он сохранился.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                )
            }
        }
        items(likes, key = { it.track.id }) { l ->
            TrackRow(l.track, l.coverPath, liked = true, extra = { SearchMenu(l.track) }) { onUnlike(l.track.id) }
        }
    }
}

@Composable
private fun CleanScreen(state: RadioState, onExit: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { onExit() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(44.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = state.now?.art,
                contentDescription = "Обложка",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val streamingServices = listOf(
    "Spotify" to "https://open.spotify.com/search/",
    "YouTube Music" to "https://music.youtube.com/search?q=",
    "Яндекс Музыка" to "https://music.yandex.ru/search?text=",
    "VK Музыка" to "https://vk.com/audio?q=",
    "Apple Music" to "https://music.apple.com/search?term=",
    "SoundCloud" to "https://soundcloud.com/search?q=",
)

@Composable
private fun SearchMenu(track: Track) {
    var open by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val query = Uri.encode("${track.artist} ${track.title}")
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.Search, contentDescription = "Найти в стриминговых сервисах")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            streamingServices.forEach { (name, url) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        open = false
                        runCatching { uriHandler.openUri(url + query) }
                    },
                )
            }
        }
    }
}
