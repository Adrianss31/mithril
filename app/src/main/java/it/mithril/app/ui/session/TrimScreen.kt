package it.mithril.app.ui.session

import android.view.TextureView
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.mithril.app.data.LocalVideos
import it.mithril.app.data.VideoEntity
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Trim
import it.mithril.app.ui.Dest
import it.mithril.app.ui.LocalNav
import it.mithril.app.ui.components.LocalToast
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.components.RoundIconButton
import it.mithril.app.ui.components.TextLink
import it.mithril.app.ui.rememberRepo
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.PlayerInk
import it.mithril.app.ui.theme.T
import it.mithril.app.ui.theme.ts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Inizio e fine di un video scelti guardandolo: si scorre la barra e si tocca "Segna qui". */
@Composable
fun TrimScreen(d: Dest.Trim) {
    val repo = rememberRepo()
    val nav = LocalNav.current
    // Letto dal database all'apertura (il video può essere stato appena salvato) e poi fissato.
    var loaded by remember { mutableStateOf(false) }
    var video by remember { mutableStateOf<VideoEntity?>(null) }
    LaunchedEffect(d.videoId) { video = repo.dao.video(d.videoId); loaded = true }
    if (!loaded) { Box(Modifier.fillMaxSize().background(M.c.player)); return }
    val v = video ?: run { LaunchedEffect(Unit) { nav.pop() }; return }
    TrimEditor(v)
}

@Composable
private fun TrimEditor(v: VideoEntity) {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val toast = LocalToast.current
    val context = LocalContext.current
    var start by remember { mutableIntStateOf(v.startSec) }
    var end by remember { mutableStateOf(v.endSec) }
    val changed = start != v.startSec || end != v.endSec
    var scrub by remember { mutableStateOf<Float?>(null) }
    var unavailable by remember { mutableStateOf<String?>(null) }

    val yt = remember { YouTubePlayerState() }
    var web by remember { mutableStateOf<WebView?>(null) }
    val local = remember { LocalEngine(context) }
    val engine: MediaEngine = if (v.isLocal) local else yt
    val duration = engine.duration.takeIf { it > 0f } ?: (v.durationSec ?: 0).toFloat()
    val position = scrub ?: engine.position

    LaunchedEffect(Unit) {
        if (v.isLocal) {
            val uri = v.localUri!!
            if (LocalVideos.readable(context, uri)) local.load(uri, v.startSec.toFloat()) else unavailable = "File non trovato sul telefono"
        } else web = createYouTubeWebView(context, v.youtubeId, yt, v.startSec.toFloat())
    }
    LaunchedEffect(Unit) { while (true) { delay(250); if (v.isLocal) local.tick() } }
    LaunchedEffect(engine.duration) { engine.duration.toInt().let { if (it > 0) repo.learnDuration(v.id, it) } }
    LaunchedEffect(yt.error) {
        val e = yt.error ?: return@LaunchedEffect
        unavailable = if (e in YouTubePlayerState.NOT_EMBEDDABLE || e == 152 || e == 153) "Il canale non permette di guardarlo dentro Mithril" else "Video non disponibile"
    }
    LaunchedEffect(local.error) { if (local.error != null) unavailable = "Non riesco a riprodurre questo file" }

    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val obs = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_PAUSE) engine.pause() }
        lifecycle.addObserver(obs)
        onDispose {
            view.keepScreenOn = false
            lifecycle.removeObserver(obs)
            yt.web?.apply { stopLoading(); loadUrl("about:blank"); destroy() }
            yt.web = null
            local.release()
        }
    }

    fun seek(sec: Float) = engine.seek(sec.coerceIn(0f, duration.takeIf { it > 0f } ?: sec))
    fun save() {
        val s = start; val e = Trim.end(end, duration.toInt())
        repo.scope.launch { repo.setTrim(v.id, s, e) }
    }
    fun leave() {
        engine.pause()
        if (changed) toast.show("Inizio e fine non salvati", "Salva") { save(); toast.show("Inizio e fine salvati") }
        nav.pop()
    }
    BackHandler(onBack = ::leave)

    fun markStart() {
        val pos = position.toInt()
        val e = end ?: duration.toInt().takeIf { it > 0 }
        if (e != null && e - pos < Trim.MIN_SEC) return toast.show("Troppo vicino alla fine: servono almeno ${Trim.MIN_SEC} secondi")
        start = pos
    }
    fun markEnd() {
        val pos = position.toInt()
        if (pos - start < Trim.MIN_SEC) return toast.show("Troppo vicino all'inizio: servono almeno ${Trim.MIN_SEC} secondi")
        end = Trim.end(pos, duration.toInt())
    }

    Column(Modifier.fillMaxSize().background(M.c.player).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton("keyboard_arrow_down", ::leave, size = 44.dp, iconSize = 28.dp, bg = Color.Transparent, fg = PlayerInk, contentDescription = "Chiudi")
            Text("Inizio e fine", style = T.secondary, color = PlayerInk.copy(alpha = 0.7f), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Spacer(Modifier.width(44.dp))
        }
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black), contentAlignment = Alignment.Center) {
            when {
                unavailable != null -> Text(unavailable!!, style = T.body14, color = PlayerInk.copy(alpha = 0.8f), textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                v.isLocal -> AndroidView(
                    factory = { TextureView(it) },
                    update = { local.exo.setVideoTextureView(it) },
                    modifier = Modifier.aspectRatio(local.aspect, matchHeightConstraintsFirst = local.aspect < 16f / 9f),
                )
            }
            web?.let { NativeHost(it, visible = unavailable == null, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(14.dp))
            Text(v.title, style = T.title16, color = PlayerInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text("Scorri la barra o guarda il video, poi tocca \"Segna qui\" dove finisce l'intro e dove comincia l'outro.", style = T.secondary, color = PlayerInk.copy(alpha = 0.6f))
            Spacer(Modifier.height(14.dp))
            Timeline(
                duration, position, start.toFloat(), (end ?: duration.toInt()).toFloat(),
                onScrub = { scrub = it; if (v.isLocal) seek(it) },
                onSeek = { scrub = null; seek(it) },
            )
            Row {
                Text(if (position < 1f) "0:00" else Fmt.clock(position.toInt()), style = T.caption, color = PlayerInk.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                Text(Fmt.clock(duration.toInt()), style = T.caption, color = PlayerInk.copy(alpha = 0.6f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                StepPill("−10 s") { seek(position - 10f) }
                RoundIconButton(
                    if (engine.playing) "pause" else "play_arrow",
                    { if (engine.playing) engine.pause() else engine.play() },
                    size = 64.dp, iconSize = 34.dp, bg = M.c.accent, fg = M.c.onAccent, filled = true,
                    contentDescription = if (engine.playing) "Pausa" else "Riproduci",
                )
                StepPill("+10 s") { seek(position + 10f) }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MarkCard(
                    "Parte da", if (start > 0) Fmt.clock(start) else "dall'inizio", Modifier.weight(1f),
                    onMark = ::markStart, onTry = { seek(start.toFloat()); engine.play() },
                )
                MarkCard(
                    "Finisce a", end?.let { Fmt.clock(it) } ?: "alla fine", Modifier.weight(1f),
                    onMark = ::markEnd, onTry = { seek(((end ?: duration.toInt()) - 5).toFloat()); engine.play() },
                )
            }
            if (start > 0 || end != null) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("Guarderai ${Fmt.clock(((end ?: duration.toInt()) - start).coerceAtLeast(0))} · ", style = T.secondary, color = PlayerInk.copy(alpha = 0.6f))
                    TextLink("Azzera", { start = 0; end = null })
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        PrimaryButton(
            "Salva", {
                save()
                toast.show(if (start > 0 || end != null) "Inizio e fine salvati" else "Il video si guarda tutto")
                engine.pause(); nav.pop()
            },
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 12.dp),
            enabled = changed,
        )
    }
}

/** Barra del video: tutta la durata, con evidenziata la parte che si guarda e la posizione attuale. */
@Composable
private fun Timeline(duration: Float, position: Float, start: Float, end: Float, onScrub: (Float) -> Unit, onSeek: (Float) -> Unit) {
    val accent = M.c.accent
    val dur by rememberUpdatedState(duration)
    val scrubTo by rememberUpdatedState(onScrub)
    val seekTo by rememberUpdatedState(onSeek)
    Box(
        Modifier.fillMaxWidth().height(44.dp)
            .pointerInput(Unit) { detectTapGestures { o -> if (dur > 0f) seekTo((o.x / size.width).coerceIn(0f, 1f) * dur) } }
            .pointerInput(Unit) {
                var last = 0f
                detectHorizontalDragGestures(
                    onDragEnd = { seekTo(last) },
                    onDragCancel = { seekTo(last) },
                ) { change, _ ->
                    if (dur > 0f) { last = (change.position.x / size.width).coerceIn(0f, 1f) * dur; scrubTo(last) }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (duration <= 0f) return@Canvas
            val cy = size.height / 2
            val h = 6.dp.toPx()
            fun x(sec: Float) = (sec / duration).coerceIn(0f, 1f) * size.width
            drawRoundRect(PlayerInk.copy(alpha = 0.15f), Offset(0f, cy - h / 2), Size(size.width, h), CornerRadius(h / 2))
            drawRoundRect(accent.copy(alpha = 0.55f), Offset(x(start), cy - h / 2), Size(x(end) - x(start), h), CornerRadius(h / 2))
            val mw = 3.dp.toPx(); val mh = 20.dp.toPx()
            listOf(start, end).forEach { drawRoundRect(accent, Offset(x(it) - mw / 2, cy - mh / 2), Size(mw, mh), CornerRadius(mw / 2)) }
            drawCircle(PlayerInk, 8.dp.toPx(), Offset(x(position), cy))
        }
    }
}

@Composable
private fun MarkCard(label: String, value: String, modifier: Modifier, onMark: () -> Unit, onTry: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(PlayerInk.copy(alpha = 0.07f)).padding(14.dp)) {
        Text(label, style = T.caption, color = PlayerInk.copy(alpha = 0.6f))
        Text(value, style = ts(22, FontWeight.SemiBold), color = PlayerInk)
        Spacer(Modifier.height(10.dp))
        Text(
            "Segna qui", style = T.button15, color = M.c.onAccent, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(M.c.accent).clickable(onClick = onMark).padding(vertical = 10.dp),
        )
        Text(
            "Prova", style = T.secondary.copy(fontWeight = FontWeight.Medium), color = PlayerInk.copy(alpha = 0.75f), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onTry).padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun StepPill(text: String, onClick: () -> Unit) {
    Text(
        text, style = T.secondary.copy(fontWeight = FontWeight.Medium), color = PlayerInk.copy(alpha = 0.8f),
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(PlayerInk.copy(alpha = 0.08f))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    )
}
