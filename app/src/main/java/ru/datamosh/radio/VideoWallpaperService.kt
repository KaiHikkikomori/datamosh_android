package ru.datamosh.radio

import android.net.Uri
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/** Live wallpaper: plays the cached background video (filesDir/bg.webm), cropped to fill the screen. */
class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    private inner class VideoEngine : Engine() {
        private var player: ExoPlayer? = null

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            val file = File(filesDir, "bg.webm")
            if (!file.exists() || file.length() == 0L) {
                AppLog.e("WALLPAPER", "Background video is missing: ${file.absolutePath}")
                return
            }
            AppLog.d("WALLPAPER", "Surface created, starting video")
            player = ExoPlayer.Builder(applicationContext).build().apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                setVideoSurfaceHolder(holder)
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                prepare()
                playWhenReady = isVisible
            }
        }

        // Pause when the wallpaper is not on screen to save battery.
        override fun onVisibilityChanged(visible: Boolean) {
            player?.playWhenReady = visible
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            AppLog.d("WALLPAPER", "Surface destroyed")
            player?.release()
            player = null
            super.onSurfaceDestroyed(holder)
        }
    }
}
