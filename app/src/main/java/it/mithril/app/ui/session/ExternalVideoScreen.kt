package it.mithril.app.ui.session

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mithril.app.ui.rememberRepo
import it.mithril.app.ui.statusBarTop
import kotlinx.coroutines.launch
import it.mithril.app.data.YouTube
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Sessions
import it.mithril.app.ui.Dest
import it.mithril.app.ui.LocalNav
import it.mithril.app.ui.components.Card
import it.mithril.app.ui.components.LocalToast
import it.mithril.app.ui.components.OutlineButton
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.components.RoundIconButton
import it.mithril.app.ui.components.Symbol
import it.mithril.app.ui.components.Thumb
import it.mithril.app.ui.components.TonalButton
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T

/** Il canale non permette l'incorporamento: si apre su YouTube e al ritorno si chiede com'è andata. */
@Composable
fun ExternalVideoScreen(d: Dest.External) {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val toast = LocalToast.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by repo.videos.collectAsStateWithLifecycle()
    val videos = Sessions.videosOf(d.req.videoIds, all ?: emptyList())
    val v = videos.firstOrNull { it.id == d.videoId } ?: videos.firstOrNull() ?: return
    var opened by rememberSaveable { mutableStateOf(false) }
    var back by rememberSaveable { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_RESUME && opened) back = true }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    fun finish(mini: Boolean) {
        if (finishing) return
        finishing = true
        scope.launch {
            val miniVideo = if (mini) Sessions.miniFor(videos, all ?: emptyList()) else null
            val ids = if (miniVideo != null) listOf(miniVideo.id) else videos.map { it.id }
            val title = if (miniVideo != null) miniVideo.title else Sessions.title(videos)
            val sec = if (miniVideo != null) miniVideo.durationSec ?: 0 else Sessions.totalSec(videos)
            val id = repo.logSession(d.req.slotId, mini, 1f, ids, title, sec)
            nav.replace(Dest.Completion(id, d.req.copy(mini = mini, videoIds = ids), ids.size))
        }
    }

    Column(Modifier.fillMaxSize().background(M.c.bg).verticalScroll(rememberScrollState()).padding(top = statusBarTop() + 6.dp, bottom = 24.dp)) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton("arrow_back", { nav.pop() }, size = 44.dp, bg = M.c.bg, contentDescription = "Indietro")
            Text(d.req.label, style = T.title16, color = M.c.ink)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                Thumb(v.thumbnailUrl, maxWidth, maxWidth * 9f / 16f, radius = 22.dp, duration = Fmt.clock(v.durationSec))
            }
            Spacer(Modifier.height(16.dp))
            Text(v.title, style = T.heroTitle, color = M.c.ink, modifier = Modifier.padding(horizontal = 4.dp))
            Text("${v.channel} · ${Fmt.minutes(v.durationSec).trimEnd('\'')} min", style = T.secondary, color = M.c.ink2, modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Row {
                    Symbol("info", 22.dp, M.c.accent)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Il canale non permette di riprodurre questo video dentro Mithril. Lo apriamo su YouTube: al ritorno ti chiediamo com'è andata.",
                        style = T.body, color = M.c.ink,
                    )
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Apri su YouTube", {
                    opened = true
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(YouTube.watchUrl(v.youtubeId))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }.onFailure { toast.show("Non riesco ad aprire YouTube") }
                }, Modifier.fillMaxWidth(), icon = "open_in_new", iconFilled = false)
            }
            AnimatedVisibility(back, enter = fadeIn(tween(400)) + slideInVertically(tween(450)) { it / 4 }) {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("Bentornato", style = T.secondary, color = M.c.ink2)
                    Text("Fatto?", style = T.heroTitle, color = M.c.ink)
                    Spacer(Modifier.height(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton("Sì, tutto", { finish(false) }, Modifier.fillMaxWidth(), icon = "check")
                        TonalButton("Ho fatto la versione mini", { finish(true) }, Modifier.fillMaxWidth())
                        OutlineButton("No, non oggi", {
                            toast.show("Va bene. Domani bastano 10 minuti.")
                            nav.pop()
                        }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
