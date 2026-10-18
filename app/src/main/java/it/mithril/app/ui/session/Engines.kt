package it.mithril.app.ui.session

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer

/** Ciò che serve al player per qualunque sorgente: YouTube incorporato, file locale o TV via Cast. */
@Stable
interface MediaEngine {
    /** -1 non avviato, 0 finito, 1 in riproduzione, 2 in pausa, 3 buffering, 5 pronto. */
    val state: Int
    val position: Float
    val duration: Float
    val playing: Boolean get() = state == 1 || state == 3
    /** Chiamato a fine video (impostato dal player a ogni composizione). */
    var onEnded: (() -> Unit)?
    fun play()
    fun pause()
    fun seek(sec: Float)
}

/** File del telefono riprodotto con ExoPlayer su una TextureView. */
@Stable
class LocalEngine(context: Context) : MediaEngine {
    val exo: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
    override var state by mutableIntStateOf(-1)
        private set
    override var position by mutableFloatStateOf(0f)
        private set
    override var duration by mutableFloatStateOf(0f)
        private set
    var aspect by mutableFloatStateOf(16f / 9f)
        private set
    var error by mutableStateOf<String?>(null)
    override var onEnded: (() -> Unit)? = null
    var loadedUri: String? = null
        private set

    init {
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) {
                when (s) {
                    Player.STATE_ENDED -> { state = 0; onEnded?.invoke() }
                    Player.STATE_BUFFERING -> state = 3
                    Player.STATE_READY -> state = if (exo.playWhenReady) 1 else 2
                }
                tick()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (exo.playbackState == Player.STATE_READY) state = if (isPlaying) 1 else 2
            }
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) aspect = size.width * size.pixelWidthHeightRatio / size.height
            }
            override fun onPlayerError(e: PlaybackException) { error = e.errorCodeName }
        })
    }

    fun load(uri: String, startSec: Float = 0f, autoplay: Boolean = true) {
        loadedUri = uri
        error = null; state = -1; position = startSec; duration = 0f
        exo.setMediaItem(MediaItem.fromUri(Uri.parse(uri)), (startSec * 1000).toLong())
        exo.prepare()
        exo.playWhenReady = autoplay
    }

    /** Aggiorna posizione e durata (chiamato periodicamente dal player). */
    fun tick() {
        position = exo.currentPosition / 1000f
        val d = exo.duration
        if (d > 0) duration = d / 1000f
    }

    override fun play() { if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0); exo.play() }
    override fun pause() = exo.pause()
    override fun seek(sec: Float) { exo.seekTo((sec * 1000).toLong()); tick() }
    fun release() = exo.release()
}
