package it.mithril.app.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mithril.app.ui.rememberRepo
import kotlinx.coroutines.launch
import it.mithril.app.data.Tags
import it.mithril.app.data.VideoEntity
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Sessions
import it.mithril.app.ui.Dest
import it.mithril.app.ui.LocalNav
import it.mithril.app.ui.SessionRequest
import it.mithril.app.ui.components.LocalToast
import it.mithril.app.ui.components.OutlineButton
import it.mithril.app.ui.components.RoundIconButton
import it.mithril.app.ui.components.Thumb
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.PlayerInk
import it.mithril.app.ui.theme.T
import android.view.TextureView
import android.webkit.WebView
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import it.mithril.app.cast.Cast
import it.mithril.app.cast.CastEngine
import it.mithril.app.data.LocalVideos
import it.mithril.app.data.Progress
import it.mithril.app.data.SessionProgress
import it.mithril.app.widget.Widgets
import it.mithril.app.ui.SheetKind
import it.mithril.app.ui.components.Symbol
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.snapshotFlow

@Composable
fun PlayerScreen(req: SessionRequest) {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val all by repo.videos.collectAsStateWithLifecycle()
    // La sequenza è fissata all'apertura: cambi in Libreria non la stravolgono a metà.
    val videos = remember(all == null) { Sessions.videosOf(req.videoIds, all ?: emptyList()) }
    if (all == null) { Box(Modifier.fillMaxSize().background(M.c.player)); return }
    if (videos.isEmpty()) { LaunchedEffect(Unit) { toast.show("Video non trovati"); nav.pop() }; return }

    // Sessione di oggi iniziata e lasciata a metà: si riprende da lì.
    val resume = remember { req.slotId?.takeIf { !req.mini }?.let { SessionProgress.get(context, it) }?.takeIf { it.index in videos.indices } }
    var index by remember { mutableIntStateOf(resume?.index ?: 0) }
    var startAt by remember { mutableFloatStateOf(resume?.position ?: 0f) }
    val learned = remember { mutableStateListOf<Int>().apply { addAll(videos.map { it.durationSec ?: 0 }) } }
    var watched by remember { mutableFloatStateOf(resume?.watched ?: 0f) }
    var lastPos by remember { mutableFloatStateOf(0f) }
    var finishing by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val yt = remember { YouTubePlayerState() }
    var web by remember { mutableStateOf<WebView?>(null) }
    val local = remember { LocalEngine(context) }
    val castUi by Cast.state.collectAsStateWithLifecycle()
    // Avvio dal widget: prima si collega la TV predefinita, poi parte il primo video del telefono.
    var autoCasting by remember { mutableStateOf(req.autoCast) }
    var autoCastName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        Cast.init(context)
        if (!autoCasting) return@LaunchedEffect
        val s = repo.settings.now()
        val id = s.castDeviceId
        val name = s.castDeviceName
        when {
            id == null || name == null || !Cast.state.value.available -> {}
            videos.none { it.isLocal } -> toast.show("I video di YouTube si guardano sul telefono: in TV vanno solo quelli del telefono")
            else -> {
                autoCastName = name
                if (Cast.autoConnect(id, name) == null) toast.show("$name non risponde: il video parte sul telefono")
                else withTimeoutOrNull(2_000) { snapshotFlow { castUi.connectedTo }.first { it != null } }
            }
        }
        autoCasting = false
    }
    val current = videos[index]
    val casting = castUi.connectedTo != null && current.isLocal
    val engine: MediaEngine = when { casting -> Cast.engine; current.isLocal -> local; else -> yt }
    val total = learned.sum().coerceAtLeast(1)
    val ratio = (watched / total).coerceIn(0f, 1f)

    fun complete(mini: Boolean = req.mini) {
        if (finishing) return
        finishing = true
        engine.pause()
        SessionProgress.clear(context)
        scope.launch {
            val done = (0..index).count { k -> k < index || engine.state == 0 || engine.position > (learned[k] * 0.8f) }
            val ids = videos.map { it.id }
            val id = repo.logSession(req.slotId, mini, ratio, ids, Sessions.title(videos), total)
            nav.replace(Dest.Completion(id, req.copy(mini = mini), done.coerceAtLeast(1)))
        }
    }
    fun leave(d: Dest) { leaving = true; engine.pause(); nav.replace(d) }
    fun goNext() { if (index < videos.lastIndex) index++ else complete() }

    // Carica il video corrente nel motore giusto: cambio video, inizio o fine della trasmissione.
    var wasCasting by remember { mutableStateOf(false) }
    LaunchedEffect(index, casting, autoCasting) {
        val v = current
        if (autoCasting && v.isLocal) return@LaunchedEffect
        when {
            v.isLocal -> {
                yt.pause()
                val uri = v.localUri!!
                if (!LocalVideos.readable(context, uri)) { toast.show("File non trovato sul telefono, passo al successivo"); goNext(); return@LaunchedEffect }
                if (casting) {
                    // Collegata a video iniziato la TV riparte dall'inizio (il collegamento richiede qualche secondo);
                    // collegata dal widget prima di partire, riprende la sessione da dove era rimasta.
                    local.pause()
                    Cast.play(context, uri, LocalVideos.mimeType(context, uri), v.title, startAt)?.let { toast.show(it); Cast.disconnect() }
                } else if (local.loadedUri == uri) {
                    if (wasCasting) local.seek(Cast.engine.position)
                    local.play()
                } else local.load(uri, startAt)
            }
            v.embeddable == false -> leave(Dest.External(req, v.id))
            else -> {
                local.pause()
                if (wasCasting) Cast.engine.stop()
                if (web == null) web = createYouTubeWebView(context, v.youtubeId, yt, startAt) else yt.load(v.youtubeId, startAt)
            }
        }
        wasCasting = casting
        startAt = 0f
        lastPos = engine.position
    }
    // Avanzamento salvato per riprendere (e per il widget "Primo video").
    fun saveProgress() {
        val slot = req.slotId ?: return
        if (req.mini || finishing) return
        SessionProgress.save(context, Progress(slot, java.time.LocalDate.now().toEpochDay(), index, engine.position, watched, total))
    }
    val latestSave = rememberUpdatedState { saveProgress() }
    val latestPlaying = rememberUpdatedState(engine.playing)
    LaunchedEffect(index) { if (index > 0 || watched > 0f) { saveProgress(); Widgets.updateAll(context) } }
    LaunchedEffect(Unit) { while (true) { delay(10_000); if (latestPlaying.value) latestSave.value() } }
    // Le posizioni di ExoPlayer e della TV vanno lette di continuo.
    LaunchedEffect(engine) {
        while (true) {
            delay(300)
            when (engine) { is LocalEngine -> engine.tick(); is CastEngine -> engine.tick(); else -> {} }
        }
    }
    SideEffect {
        yt.onEnded = { if (!current.isLocal) goNext() }
        local.onEnded = { if (current.isLocal && !casting) goNext() }
        Cast.engine.onEnded = { if (casting) goNext() }
    }
    // Durata letta dal player.
    LaunchedEffect(engine.duration, index) {
        val d = engine.duration.toInt()
        if (d > 0 && learned[index] <= 0) { learned[index] = d; repo.learnDuration(current.id, d) }
    }
    // Tempo guardato: solo l'avanzamento naturale (niente salti in avanti).
    LaunchedEffect(engine.position) {
        val delta = engine.position - lastPos
        if (engine.playing && delta > 0f && delta < 2.5f) watched += delta
        lastPos = engine.position
    }
    LaunchedEffect(yt.error) {
        val e = yt.error ?: return@LaunchedEffect
        when (e) {
            in YouTubePlayerState.NOT_EMBEDDABLE -> { repo.markEmbeddable(current.id, false); leave(Dest.External(req, current.id)) }
            152, 153 -> leave(Dest.External(req, current.id))
            else -> { toast.show("Questo video non è disponibile, passo al successivo"); goNext() }
        }
    }
    LaunchedEffect(local.error) {
        if (local.error != null) { toast.show("Non riesco a riprodurre questo file, passo al successivo"); goNext() }
    }
    // Schermo acceso; pausa quando l'app va in background (la TV invece continua).
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentEngine by rememberUpdatedState(engine)
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_PAUSE && currentEngine !is CastEngine) currentEngine.pause()
        }
        lifecycle.addObserver(obs)
        onDispose {
            if (!finishing && watched > 5f) { latestSave.value(); repo.scope.launch { Widgets.updateAll(context) } }
            view.keepScreenOn = false
            lifecycle.removeObserver(obs)
            yt.web?.apply { stopLoading(); loadUrl("about:blank"); destroy() }
            yt.web = null
            local.release()
            if (Cast.state.value.connectedTo != null) Cast.disconnect()
        }
    }
    val close: () -> Unit = { if (ratio >= Sessions.DONE_RATIO) complete() else { leaving = true; engine.pause(); nav.pop() } }
    BackHandler(onBack = close)

    Column(Modifier.fillMaxSize().background(M.c.player).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton("keyboard_arrow_down", close, size = 44.dp, iconSize = 28.dp, bg = Color.Transparent, fg = PlayerInk, contentDescription = "Chiudi")
            Text(req.label, style = T.secondary, color = PlayerInk.copy(alpha = 0.7f), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            if (castUi.available && current.isLocal) {
                RoundIconButton(
                    if (casting) "cast_connected" else "cast", { if (!casting) local.pause(); nav.sheet = SheetKind.CastPicker },
                    size = 44.dp, iconSize = 24.dp, bg = Color.Transparent, fg = if (casting) M.c.accent else PlayerInk, contentDescription = "Trasmetti alla TV",
                )
            } else Spacer(Modifier.width(44.dp))
        }
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black), contentAlignment = Alignment.Center) {
            if (current.isLocal) {
                if (casting || (autoCasting && autoCastName != null)) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Symbol("tv", 44.dp, PlayerInk.copy(alpha = 0.8f))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (casting) "In riproduzione su ${castUi.connectedTo}" else "Mi collego a ${autoCastName ?: "TV"}…",
                        style = T.body14, color = PlayerInk.copy(alpha = 0.8f),
                    )
                } else AndroidView(
                    factory = { TextureView(it) },
                    update = { local.exo.setVideoTextureView(it) },
                    modifier = Modifier.aspectRatio(local.aspect, matchHeightConstraintsFirst = local.aspect < 16f / 9f),
                )
            }
            web?.let { NativeHost(it, visible = !finishing && !leaving && !current.isLocal, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(14.dp))
            Segments(learned, index, if (engine.duration > 0) engine.position / engine.duration else 0f)
            Spacer(Modifier.height(18.dp))
            Text("Video ${index + 1} di ${videos.size} · ${Tags.kind(current.tagList)}", style = T.secondary, color = PlayerInk.copy(alpha = 0.6f))
            Spacer(Modifier.height(4.dp))
            Text(current.title, style = T.heroTitle, color = PlayerInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(current.channel, style = T.secondary, color = PlayerInk.copy(alpha = 0.6f))
            Spacer(Modifier.height(18.dp))
            val prog by animateFloatAsState(if (engine.duration > 0) (engine.position / engine.duration).coerceIn(0f, 1f) else 0f, tween(450), label = "prog")
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(PlayerInk.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(prog).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(M.c.accent))
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Text(if (engine.position < 1f) "0:00" else Fmt.clock(engine.position.toInt()), style = T.caption, color = PlayerInk.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                Text(Fmt.clock(if (engine.duration > 0) engine.duration.toInt() else learned[index]), style = T.caption, color = PlayerInk.copy(alpha = 0.6f))
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton("replay", { engine.seek(0f); engine.play() }, size = 52.dp, iconSize = 28.dp, bg = Color.Transparent, fg = PlayerInk, contentDescription = "Ricomincia il video")
                RoundIconButton(
                    if (engine.playing) "pause" else "play_arrow",
                    { if (engine.playing) engine.pause() else engine.play() },
                    size = 76.dp, iconSize = 38.dp, bg = M.c.accent, fg = M.c.onAccent, filled = true,
                    contentDescription = if (engine.playing) "Pausa" else "Riproduci",
                )
                RoundIconButton("skip_next", { goNext() }, size = 52.dp, iconSize = 30.dp, bg = Color.Transparent, fg = PlayerInk, enabled = !finishing, contentDescription = "Video successivo")
            }
            Spacer(Modifier.height(18.dp))
            videos.getOrNull(index + 1)?.let { NextCard(it) }
            AnimatedVisibility(ratio >= Sessions.DONE_RATIO, enter = fadeIn(tween(400)) + expandVertically(tween(400))) {
                Text("✓ Oltre l'80%: la sessione conta già.", style = T.body14.copy(fontWeight = FontWeight.Medium), color = M.c.accent, modifier = Modifier.padding(top = 14.dp))
            }
            Spacer(Modifier.height(18.dp))
        }
        OutlineButton(
            "Segna come fatta", { complete() },
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 12.dp),
            color = PlayerInk, borderColor = PlayerInk.copy(alpha = 0.25f), height = 54.dp, enabled = !finishing,
        )
    }
}


/** Un segmento per video, larghezza proporzionale ai minuti. */
@Composable
private fun Segments(durations: List<Int>, index: Int, progress: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        durations.forEachIndexed { i, d ->
            val w = d.coerceAtLeast(60).toFloat()
            Box(
                Modifier.weight(w).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (i < index) M.c.accent else PlayerInk.copy(alpha = if (i == index) 0.35f else 0.15f))
            ) {
                if (i == index) Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(M.c.accent))
            }
        }
    }
}

@Composable
private fun NextCard(v: VideoEntity) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PlayerInk.copy(alpha = 0.07f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(v.thumbnailUrl, 64.dp, 40.dp, radius = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Dopo", style = T.caption, color = PlayerInk.copy(alpha = 0.55f))
            Text(v.title, style = T.title15, color = PlayerInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(Fmt.clock(v.durationSec), style = T.secondary, color = PlayerInk.copy(alpha = 0.6f))
    }
}
