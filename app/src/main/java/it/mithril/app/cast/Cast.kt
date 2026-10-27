package it.mithril.app.cast

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import it.mithril.app.ui.session.MediaEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull

/** Ricevitore multimediale predefinito di Google: ce l'hanno Google TV e Chromecast, nessuna registrazione necessaria. */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder().setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID).build()
    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

data class CastDevice(val id: String, val name: String, val description: String?)

data class CastUiState(
    val available: Boolean = false,
    val devices: List<CastDevice> = emptyList(),
    val connecting: Boolean = false,
    val connectedTo: String? = null,
)

/** Trasmissione dei video del telefono alla TV con Google Cast. */
object Cast {
    private val _state = MutableStateFlow(CastUiState())
    val state: StateFlow<CastUiState> = _state.asStateFlow()
    private var context: CastContext? = null
    private var router: MediaRouter? = null
    private var selector: MediaRouteSelector? = null
    private var server: LocalMediaServer? = null
    val engine = CastEngine()

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(r: MediaRouter, route: MediaRouter.RouteInfo) = refreshRoutes()
        override fun onRouteRemoved(r: MediaRouter, route: MediaRouter.RouteInfo) = refreshRoutes()
        override fun onRouteChanged(r: MediaRouter, route: MediaRouter.RouteInfo) = refreshRoutes()
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(s: CastSession, id: String) = connected(s)
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) = connected(s)
        override fun onSessionEnded(s: CastSession, error: Int) = disconnected()
        override fun onSessionStartFailed(s: CastSession, error: Int) = disconnected()
        override fun onSessionSuspended(s: CastSession, reason: Int) {}
        override fun onSessionStarting(s: CastSession) { _state.value = _state.value.copy(connecting = true) }
        override fun onSessionEnding(s: CastSession) {}
        override fun onSessionResuming(s: CastSession, id: String) {}
        override fun onSessionResumeFailed(s: CastSession, error: Int) = disconnected()
    }

    /** Da chiamare all'avvio del player: senza Google Play Services la trasmissione semplicemente non c'è. */
    fun init(app: Context) {
        if (context != null) return
        val cc = runCatching { @Suppress("DEPRECATION") CastContext.getSharedInstance(app.applicationContext) }.getOrNull() ?: return
        context = cc
        router = MediaRouter.getInstance(app.applicationContext)
        selector = cc.mergedSelector ?: MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)).build()
        cc.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        _state.value = _state.value.copy(available = true)
        cc.sessionManager.currentCastSession?.takeIf { it.isConnected }?.let { connected(it) }
    }

    fun startDiscovery() {
        val r = router ?: return; val sel = selector ?: return
        r.addCallback(sel, routerCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        refreshRoutes()
    }

    fun stopDiscovery() { router?.removeCallback(routerCallback) }

    private fun refreshRoutes() {
        val r = router ?: return; val sel = selector ?: return
        val routes = r.routes.filter { !it.isDefault && it.isEnabled && it.matchesSelector(sel) }
            .map { CastDevice(it.id, it.name, it.description) }
        _state.value = _state.value.copy(devices = routes)
    }

    fun connect(id: String) {
        val route = router?.routes?.firstOrNull { it.id == id } ?: return
        _state.value = _state.value.copy(connecting = true)
        router?.selectRoute(route)
    }

    /**
     * Collega da sola la TV scelta nelle impostazioni (cercata per id, poi per nome).
     * Restituisce il nome della TV collegata, o null se non si trova o non risponde in tempo.
     */
    suspend fun autoConnect(id: String, name: String): String? {
        _state.value.connectedTo?.let { return it }
        if (router == null) return null
        startDiscovery()
        try {
            val device = withTimeoutOrNull(12_000) {
                state.mapNotNull { s -> s.devices.firstOrNull { it.id == id } ?: s.devices.firstOrNull { it.name == name } }.first()
            } ?: return null
            connect(device.id)
            return withTimeoutOrNull(25_000) { state.first { it.connectedTo != null || !it.connecting } }?.connectedTo
        } finally {
            stopDiscovery()
        }
    }

    fun disconnect() {
        context?.sessionManager?.endCurrentSession(true)
        disconnected()
    }

    private fun connected(s: CastSession) {
        _state.value = _state.value.copy(connecting = false, connectedTo = s.castDevice?.friendlyName ?: "TV")
        engine.attach(s.remoteMediaClient)
    }

    private fun disconnected() {
        server?.stop(); server = null
        engine.attach(null)
        _state.value = _state.value.copy(connecting = false, connectedTo = null)
    }

    /** Manda alla TV un file del telefono, partendo da [startSec]. Restituisce un messaggio d'errore o null. */
    fun play(app: Context, uri: String, mime: String, title: String, startSec: Float): String? {
        val client = context?.sessionManager?.currentCastSession?.remoteMediaClient ?: return "TV non collegata"
        server?.stop()
        val srv = LocalMediaServer(app.applicationContext, Uri.parse(uri), mime)
        val url = srv.url() ?: run { srv.stop(); return "Telefono e TV devono essere sulla stessa rete Wi-Fi" }
        server = srv
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply { putString(MediaMetadata.KEY_TITLE, title) }
        val info = MediaInfo.Builder(url).setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setContentType(mime).setMetadata(meta).build()
        engine.reset(startSec)
        client.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).setCurrentTime((startSec * 1000).toLong()).build())
        return null
    }
}

/** La TV come motore del player: comandi e avanzamento passano dal RemoteMediaClient. */
@Stable
class CastEngine : MediaEngine {
    override var state by mutableIntStateOf(-1)
        private set
    override var position by mutableFloatStateOf(0f)
        private set
    override var duration by mutableFloatStateOf(0f)
        private set
    override var onEnded: (() -> Unit)? = null
    private var client: RemoteMediaClient? = null
    private var loaded = false

    private val callback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = update()
    }

    internal fun attach(c: RemoteMediaClient?) {
        client?.unregisterCallback(callback)
        client = c
        c?.registerCallback(callback)
    }

    internal fun reset(startSec: Float) { state = 3; position = startSec; duration = 0f; loaded = false }

    /** Da chiamare periodicamente: la posizione remota non arriva da sola. */
    fun tick() {
        val c = client ?: return
        position = c.approximateStreamPosition / 1000f
        val d = c.streamDuration
        if (d > 0) duration = d / 1000f
    }

    private fun update() {
        val c = client ?: return
        tick()
        state = when (c.playerState) {
            MediaStatus.PLAYER_STATE_PLAYING -> { loaded = true; 1 }
            MediaStatus.PLAYER_STATE_PAUSED -> 2
            MediaStatus.PLAYER_STATE_BUFFERING, MediaStatus.PLAYER_STATE_LOADING -> 3
            MediaStatus.PLAYER_STATE_IDLE ->
                if (loaded && c.idleReason == MediaStatus.IDLE_REASON_FINISHED) {
                    loaded = false; state = 0; onEnded?.invoke(); 0
                } else state
            else -> state
        }
    }

    override fun play() { client?.play() }
    override fun pause() { client?.pause() }
    /** Ferma il video sulla TV restando collegati (il prossimo video del telefono ci torna). */
    fun stop() { client?.stop() }
    override fun seek(sec: Float) { client?.seek(MediaSeekOptions.Builder().setPosition((sec * 1000).toLong()).build()) }
}
