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
import androidx.compose.ui.viewinterop.AndroidView
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

@Composable
fun PlayerScreen(req: SessionRequest) {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val all by repo.videos.collectAsStateWithLifecycle()
    // La sequenza è fissata all'apertura: cambi in Libreria non la stravolgono a metà.
    val videos = remember(all == null) { Sessions.videosOf(req.videoIds, all ?: emptyList()) }
    if (all == null) { Box(Modifier.fillMaxSize().background(M.c.player)); return }
    if (videos.isEmpty()) { LaunchedEffect(Unit) { toast.show("Video non trovati"); nav.pop() }; return }

    var index by remember { mutableIntStateOf(0) }
    val learned = remember { mutableStateListOf<Int>().apply { addAll(videos.map { it.durationSec ?: 0 }) } }
    var watched by remember { mutableFloatStateOf(0f) }
    var lastPos by remember { mutableFloatStateOf(0f) }
    var finishing by remember { mutableStateOf(false) }
    val player = remember { YouTubePlayerState() }
    val current = videos[index]
    val total = learned.sum().coerceAtLeast(1)
    val ratio = (watched / total).coerceIn(0f, 1f)

    fun complete(mini: Boolean = req.mini) {
        if (finishing) return
        finishing = true
        scope.launch {
            val done = (0..index).count { k -> k < index || player.state == 0 || player.position > (learned[k] * 0.8f) }
            val ids = videos.map { it.id }
            val id = repo.logSession(req.slotId, mini, ratio, ids, Sessions.title(videos), total)
            nav.replace(Dest.Completion(id, req.copy(mini = mini), done.coerceAtLeast(1)))
        }
    }
    fun goNext() {
        if (index < videos.lastIndex) {
            index++; lastPos = 0f
            val v = videos[index]
            if (v.embeddable == false) nav.replace(Dest.External(req, v.id)) else player.load(v.youtubeId)
        } else complete()
    }

    // Video già noto come non incorporabile.
    LaunchedEffect(Unit) { if (videos[0].embeddable == false) nav.replace(Dest.External(req, videos[0].id)) }
    // Durata letta dal player.
    LaunchedEffect(player.duration, index) {
        val d = player.duration.toInt()
        if (d > 0 && learned[index] <= 0) { learned[index] = d; repo.learnDuration(current.id, d) }
    }
    // Tempo guardato: solo l'avanzamento naturale (niente salti in avanti).
    LaunchedEffect(player.position) {
        val delta = player.position - lastPos
        if (player.playing && delta > 0f && delta < 2.5f) watched += delta
        lastPos = player.position
    }
    LaunchedEffect(player.endedCount) { if (player.endedCount > 0) goNext() }
    LaunchedEffect(player.error) {
        val e = player.error ?: return@LaunchedEffect
        when (e) {
            in YouTubePlayerState.NOT_EMBEDDABLE -> { repo.markEmbeddable(current.id, false); nav.replace(Dest.External(req, current.id)) }
            152, 153 -> nav.replace(Dest.External(req, current.id))
            else -> { toast.show("Questo video non è disponibile, passo al successivo"); goNext() }
        }
    }
    // Schermo acceso e pausa quando l'app va in background.
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycle.addObserver(obs)
        onDispose {
            view.keepScreenOn = false
            lifecycle.removeObserver(obs)
            player.web?.apply { stopLoading(); loadUrl("about:blank"); destroy() }
            player.web = null
        }
    }
    val close: () -> Unit = { if (ratio >= Sessions.DONE_RATIO) complete() else nav.pop() }
    BackHandler(onBack = close)

    Column(Modifier.fillMaxSize().background(M.c.player).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton("keyboard_arrow_down", close, size = 44.dp, iconSize = 28.dp, bg = Color.Transparent, fg = PlayerInk, contentDescription = "Chiudi")
            Text(req.label, style = T.secondary, color = PlayerInk.copy(alpha = 0.7f), modifier = Modifier.weight(1f).padding(end = 44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            AndroidView(factory = { ctx -> createYouTubeWebView(ctx, videos[0].youtubeId, player) }, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(14.dp))
            Segments(learned, index, if (player.duration > 0) player.position / player.duration else 0f)
            Spacer(Modifier.height(18.dp))
            Text("Video ${index + 1} di ${videos.size} · ${Tags.kind(current.tagList)}", style = T.secondary, color = PlayerInk.copy(alpha = 0.6f))
            Spacer(Modifier.height(4.dp))
            Text(current.title, style = T.heroTitle, color = PlayerInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(current.channel, style = T.secondary, color = PlayerInk.copy(alpha = 0.6f))
            Spacer(Modifier.height(18.dp))
            val prog by animateFloatAsState(if (player.duration > 0) (player.position / player.duration).coerceIn(0f, 1f) else 0f, tween(450), label = "prog")
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(PlayerInk.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(prog).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(M.c.accent))
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Text(Fmt.clock(player.position.toInt().coerceAtLeast(0)).let { if (player.position < 1f) "0:00" else it }, style = T.caption, color = PlayerInk.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                Text(Fmt.clock((if (player.duration > 0) player.duration.toInt() else learned[index])), style = T.caption, color = PlayerInk.copy(alpha = 0.6f))
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton("replay", { player.seek(0f); player.play() }, size = 52.dp, iconSize = 28.dp, bg = Color.Transparent, fg = PlayerInk, contentDescription = "Ricomincia il video")
                RoundIconButton(
                    if (player.playing) "pause" else "play_arrow",
                    { if (player.playing) player.pause() else player.play() },
                    size = 76.dp, iconSize = 38.dp, bg = M.c.accent, fg = M.c.onAccent, filled = true,
                    contentDescription = if (player.playing) "Pausa" else "Riproduci",
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
