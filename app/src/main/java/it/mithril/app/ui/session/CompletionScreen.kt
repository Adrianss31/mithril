package it.mithril.app.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mithril.app.ui.rememberRepo
import it.mithril.app.ui.theme.rise
import it.mithril.app.ui.theme.ts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Sessions
import it.mithril.app.domain.Week
import it.mithril.app.ui.Dest
import it.mithril.app.ui.LocalNav
import it.mithril.app.ui.Tab
import it.mithril.app.ui.components.CompletionDots
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.components.Symbol
import it.mithril.app.ui.theme.Ease
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T

@Composable
fun CompletionScreen(d: Dest.Completion) {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val c = M.c
    val motion = M.motion
    val today by repo.today.collectAsStateWithLifecycle()
    val slots by repo.slots.collectAsStateWithLifecycle()
    val logs by repo.logs.collectAsStateWithLifecycle()
    val all by repo.videos.collectAsStateWithLifecycle()
    val videos = Sessions.videosOf(d.req.videoIds, all ?: emptyList())
    val start = Week.start(today)
    val summary = remember(slots, logs, today) { Week.summary(start, today, slots, logs, repo.weekGoal(start)) }
    var fatigue by rememberSaveable { mutableIntStateOf(6) }
    var note by rememberSaveable { mutableStateOf("") }

    val reveal = remember { Animatable(if (motion) 0f else 1f) }
    val circle = remember { Animatable(if (motion) 0f else 1f) }
    val check = remember { Animatable(if (motion) 0f else 1f) }
    val ring = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!motion) return@LaunchedEffect
        launch { reveal.animateTo(1f, tween(700, easing = Ease.Reveal)) }
        launch { delay(250); circle.animateTo(1f, spring(0.55f, 380f)) }
        launch { delay(650); check.animateTo(1f, spring(0.6f, 420f)) }
        launch { delay(400); ring.animateTo(1f, tween(1200)) }
    }
    val finish: () -> Unit = {
        scope.launch { repo.finishLog(d.logId, fatigue, note) }
        nav.popToRoot(); nav.select(Tab.TODAY)
    }
    BackHandler(onBack = finish)

    Box(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                val center = Offset(size.width * 0.5f, size.height * 0.88f)
                val r = hypot(size.width, size.height) * 1.5f * reveal.value
                val p = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
                clipPath(p, ClipOp.Intersect) { this@drawWithContent.drawContent() }
            }
            .background(c.hero)
    ) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                // Anello che si espande e svanisce
                Box(
                    Modifier.size(104.dp).graphicsLayer {
                        val s = 0.6f + 1.1f * ring.value
                        scaleX = s; scaleY = s; alpha = (0.9f * (1f - ring.value)).coerceAtLeast(0f)
                    }.border(2.dp, c.onHero, CircleShape)
                )
                Box(
                    Modifier.size(104.dp).graphicsLayer { scaleX = circle.value; scaleY = circle.value }.clip(CircleShape).background(c.onHero),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.graphicsLayer {
                        alpha = check.value.coerceIn(0f, 1f); scaleX = 0.3f + 0.7f * check.value; scaleY = 0.3f + 0.7f * check.value; rotationZ = -20f * (1f - check.value)
                    }) { Symbol("check", 60.dp, c.hero) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Sessione registrata", style = T.screenTitle, color = c.onHero, modifier = Modifier.rise(0, baseDelay = 500, stagger = 80))
            Spacer(Modifier.height(6.dp))
            val n = d.req.videoIds.size
            Text(
                "${Sessions.title(videos, short = true)} · ${d.videosDone.coerceAtMost(n)} video su $n · ${Fmt.minutes(Sessions.totalSec(videos)).trimEnd('\'')} min" +
                    if (d.req.mini) " · mini" else "",
                style = T.body14, color = c.onHero.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                modifier = Modifier.rise(1, baseDelay = 500, stagger = 80),
            )
            Spacer(Modifier.height(28.dp))
            Column(Modifier.fillMaxWidth().rise(2, baseDelay = 500, stagger = 80)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fatica percepita", style = T.title15, color = c.onHero, modifier = Modifier.weight(1f))
                    Text("$fatigue/10", style = T.title15, color = c.onHero.copy(alpha = 0.85f))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (k in 1..10) {
                        val sel = k == fatigue
                        val bg by animateColorAsState(if (sel) c.onHero else c.heroSoft, tween(250), label = "f")
                        val scale by animateFloatAsState(if (sel && motion) 1.1f else 1f, spring(0.5f, 400f), label = "fs")
                        Box(
                            Modifier.weight(1f).height(40.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(RoundedCornerShape(12.dp)).background(bg).clickable { fatigue = k },
                            contentAlignment = Alignment.Center,
                        ) { Text("$k", style = ts(14, FontWeight.SemiBold), color = if (sel) c.hero else c.onHero) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    Text("leggera", style = T.caption, color = c.onHero.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                    Text("al limite", style = T.caption, color = c.onHero.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(min = 88.dp).rise(3, baseDelay = 500, stagger = 80).clip(RoundedCornerShape(16.dp)).background(c.heroSoft).padding(14.dp)
            ) {
                if (note.isEmpty()) Text("Nota (facoltativa)", style = T.body, color = c.onHero.copy(alpha = 0.6f))
                BasicTextField(
                    note, { note = it.take(500) },
                    textStyle = T.body.copy(color = c.onHero), cursorBrush = SolidColor(c.onHero),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().rise(4, baseDelay = 500, stagger = 80).clip(RoundedCornerShape(20.dp)).background(c.heroSoft).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Questa settimana", style = T.caption, color = c.onHero.copy(alpha = 0.75f))
                    Text(
                        if (summary.active) "${summary.sessions} di ${summary.goal} — Settimana attiva ✓" else "${summary.sessions} di ${summary.goal} sessioni",
                        style = T.title15, color = c.onHero,
                    )
                }
                CompletionDots(summary.days)
            }
            Spacer(Modifier.height(110.dp))
        }
        PrimaryButton(
            "Fine", finish,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 14.dp),
            height = 56.dp, bg = c.onHero, fg = c.hero,
        )
    }
}
