package it.mithril.app.ui.today

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mithril.app.ui.rememberRepo
import it.mithril.app.ui.startSlot
import it.mithril.app.ui.statusBarTop
import it.mithril.app.ui.theme.rise
import it.mithril.app.ui.theme.ts
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import it.mithril.app.data.SessionLogEntity
import it.mithril.app.data.SlotEntity
import it.mithril.app.data.Tags
import it.mithril.app.data.VideoEntity
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Sessions
import it.mithril.app.domain.Trend
import it.mithril.app.domain.Week
import it.mithril.app.health.Health
import it.mithril.app.health.StepsState
import it.mithril.app.ui.BottomGap
import it.mithril.app.ui.Dest
import it.mithril.app.ui.LocalNav
import it.mithril.app.ui.SheetKind
import it.mithril.app.ui.Tab
import it.mithril.app.ui.components.Card
import it.mithril.app.ui.components.CheckIn
import it.mithril.app.ui.components.GrowBar
import it.mithril.app.ui.components.HeroDays
import it.mithril.app.ui.components.LocalToast
import it.mithril.app.ui.components.NumberBadge
import it.mithril.app.ui.components.Overline
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.components.RoundIconButton
import it.mithril.app.ui.components.Sparkline
import it.mithril.app.ui.components.StatValue
import it.mithril.app.ui.components.Symbol
import it.mithril.app.ui.components.TextLink
import it.mithril.app.ui.components.Thumb
import it.mithril.app.ui.components.TonalButton
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T
import it.mithril.app.update.UpdateState
import it.mithril.app.update.Updates

@Composable
fun TodayScreen() {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val today by repo.today.collectAsStateWithLifecycle()
    val slots by repo.slots.collectAsStateWithLifecycle()
    val logs by repo.logs.collectAsStateWithLifecycle()
    val videosOrNull by repo.videos.collectAsStateWithLifecycle()
    val weightsOrNull by repo.weights.collectAsStateWithLifecycle()
    val plans by repo.weekPlans.collectAsStateWithLifecycle()
    val settings by repo.appSettings.collectAsStateWithLifecycle()
    val steps by Health.state.collectAsStateWithLifecycle()
    val update by Updates.state.collectAsStateWithLifecycle()
    val videos = videosOrNull ?: emptyList()
    val goals = remember(plans) { plans.associate { it.weekStart to it.goalSessions } }
    val weekStart = Week.start(today)
    val summary = remember(today, slots, logs, goals) { Week.summary(weekStart, today, slots, logs, Week.goalFor(weekStart, goals)) }
    val streak = remember(today, slots, logs, goals) { Week.activeStreak(today, slots, logs, goals) }
    val todayLog = logs.lastOrNull { it.date == today.toEpochDay() }
    val loggedSlots = remember(logs) { logs.mapNotNull { it.slotId }.toSet() }
    val todaySlot = Sessions.todaySlots(slots, today).firstOrNull { it.id !in loggedSlots }
    val skippedYesterday = todayLog == null && Week.yesterdaySkipped(today, slots, logs)
    val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
    val next = remember(slots, logs, today) { Sessions.next(slots, logs, today, nowMin, includeToday = false) }
    val epoch = nav.tabEpoch

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Fascia hero
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(M.c.hero)
                .padding(start = 20.dp, end = 20.dp, top = statusBarTop() + 12.dp, bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.dayTitle(today), style = T.heroTitle, color = M.c.onHero, modifier = Modifier.weight(1f))
                if (streak > 0) Text(
                    if (streak == 1) "1 settimana attiva" else "$streak settimane attive",
                    style = T.secondary, color = M.c.onHero.copy(alpha = 0.85f),
                )
                Spacer(Modifier.width(8.dp))
                RoundIconButton("settings", { nav.push(Dest.Settings) }, size = 34.dp, iconSize = 20.dp, bg = M.c.heroSoft, fg = M.c.onHero, contentDescription = "Impostazioni")
            }
            Spacer(Modifier.height(16.dp))
            HeroDays(summary.days, key = epoch)
            Spacer(Modifier.height(14.dp))
            Text(
                if (summary.active) "${summary.sessions} di ${summary.goal} — Settimana attiva ✓"
                else "${summary.sessions} di ${summary.goal} sessioni · mai due volte di fila",
                style = T.secondary, color = M.c.onHero.copy(alpha = 0.9f),
            )
        }

        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            var i = 0
            if (update is UpdateState.Available) {
                val u = update as UpdateState.Available
                Card(Modifier.rise(i++, epoch), radius = 22.dp, color = M.c.accentSoft, padding = PaddingValues(16.dp), onClick = { nav.push(Dest.Settings) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Symbol("system_update", 22.dp, M.c.accentInk)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Nuova versione ${u.info.versionName}", style = T.title15, color = M.c.accentInk)
                            Text("Tocca per aggiornare Mithril", style = T.secondary, color = M.c.accentInk.copy(alpha = 0.8f))
                        }
                        Symbol("chevron_right", 22.dp, M.c.accentInk)
                    }
                }
            }
            if (skippedYesterday) {
                Box(Modifier.rise(i++, epoch).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(M.c.accentSoft).padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("Ieri è saltato, succede. Oggi bastano 10 minuti.", style = T.body14.copy(fontWeight = FontWeight.Medium), color = M.c.accentInk)
                }
            }
            when {
                todayLog != null -> DoneTodayCard(todayLog, Modifier.rise(i++, epoch)) {
                    scope.launch {
                        repo.undoLog(todayLog.id)
                        toast.show("Sessione annullata")
                    }
                }
                todaySlot != null -> SessionCard(todaySlot, videos, skippedYesterday, Modifier.rise(i++, epoch),
                    onStart = { nav.startSlot(todaySlot, false, videos) }, onMini = { nav.startSlot(todaySlot, true, videos) })
                videos.isEmpty() && slots.isEmpty() && videosOrNull != null -> FirstRunCard(Modifier.rise(i++, epoch))
                else -> RestCard(next, videos, Modifier.rise(i++, epoch))
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WeightMini(weightsOrNull?.map { it.date to it.kg } ?: emptyList(), today, Modifier.weight(1f).fillMaxHeight().rise(i, epoch))
                StepsMini(steps, settings.stepsGoal, Modifier.weight(1f).fillMaxHeight().rise(i + 1, epoch))
            }
            i += 2
            Card(Modifier.rise(i, epoch), radius = 24.dp, padding = PaddingValues(16.dp), onClick = { nav.push(Dest.Review(Week.reviewWeek(today))) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(M.c.surface2), contentAlignment = Alignment.Center) { Symbol("insights", 22.dp, M.c.ink) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Revisione della settimana", style = T.title15, color = M.c.ink)
                        Text("Domenica sera · 5 minuti, poi si pianifica", style = T.secondary, color = M.c.ink2)
                    }
                    Symbol("chevron_right", 22.dp, M.c.ink2)
                }
            }
            Spacer(Modifier.height(BottomGap))
        }
    }
}

@Composable
private fun SessionCard(slot: SlotEntity, all: List<VideoEntity>, suggestMini: Boolean, modifier: Modifier, onStart: () -> Unit, onMini: () -> Unit) {
    val vids = Sessions.videosOf(slot.videoIdList, all)
    val mini = Sessions.miniFor(vids, all)
    Card(modifier.fillMaxWidth()) {
        Overline(Fmt.overlineFor(slot.timeMin))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.time(slot.timeMin), style = T.sessionTime, color = M.c.ink)
            Spacer(Modifier.width(10.dp))
            Text("${slot.place} · ${Fmt.minutes(Sessions.totalSec(vids))}", style = T.title16, color = M.c.ink2, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(16.dp))
        VideoSequence(vids)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Glow(Modifier.weight(1f), enabled = !suggestMini) {
                PrimaryButton("Avvia", onStart, Modifier.fillMaxWidth(), icon = "play_arrow")
            }
            if (mini != null) {
                if (suggestMini) Glow(Modifier) { PrimaryButton("Mini ${Fmt.minutes(mini.durationSec)}", onMini, icon = null) }
                else TonalButton("Mini ${Fmt.minutes(mini.durationSec)}", onMini)
            }
        }
    }
}

/** Scaletta numerata con la linea verticale che collega le miniature. */
@Composable
fun VideoSequence(vids: List<VideoEntity>, modifier: Modifier = Modifier) {
    val line = M.c.line2
    Column(
        modifier.drawBehind {
            if (vids.size > 1) {
                val x = 48.dp.toPx()
                drawLine(line, Offset(x, 54.dp.toPx()), Offset(x, size.height - 54.dp.toPx()), 2.dp.toPx())
            }
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        vids.forEachIndexed { i, v ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(v.thumbnailUrl, 96.dp, 54.dp, radius = 12.dp, duration = Fmt.clock(v.durationSec), number = i + 1)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.title, style = T.title15, color = M.c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${Tags.kind(v.tagList)} · ${v.channel}", style = T.secondary, color = M.c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Alone che respira attorno ad "Avvia" (ciclo 2,8 s, parte dopo 1,2 s). */
@Composable
fun Glow(modifier: Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!M.motion || !enabled) { Box(modifier) { content() }; return }
    val t = rememberInfiniteTransition(label = "glow")
    val p by t.animateFloat(0f, 1f, InfiniteRepeatableSpec(tween(1400), RepeatMode.Reverse, StartOffset(1200)), label = "glowP")
    val color = M.c.accent.copy(alpha = 0.16f)
    Box(modifier.drawBehind {
        val s = 8.dp.toPx() * p
        drawRoundRect(color, Offset(-s, -s), Size(size.width + 2 * s, size.height + 2 * s), CornerRadius(size.height / 2 + s))
    }) { content() }
}

@Composable
private fun DoneTodayCard(log: SessionLogEntity, modifier: Modifier, onUndo: () -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(M.c.accent), contentAlignment = Alignment.Center) {
                CheckIn { Symbol("check", 26.dp, M.c.onAccent) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (log.isMini) "Fatto oggi · mini" else "Fatto oggi", style = T.cardTitle, color = M.c.ink)
                Text(
                    log.title + (log.fatigue?.let { " · fatica $it/10" } ?: ""),
                    style = T.secondary, color = M.c.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            TextLink("Annulla", onUndo)
        }
    }
}

@Composable
private fun RestCard(next: SlotEntity?, all: List<VideoEntity>, modifier: Modifier) {
    val nav = LocalNav.current
    Card(modifier.fillMaxWidth()) {
        Text("Oggi riposo", style = T.heroTitle, color = M.c.ink)
        Spacer(Modifier.height(6.dp))
        if (next != null) {
            val d = LocalDate.ofEpochDay(next.date)
            val vids = Sessions.videosOf(next.videoIdList, all)
            Text(
                "Prossima: ${Fmt.dayName(d.dayOfWeek).lowercase()} ${Fmt.time(next.timeMin)} · ${next.place} · " +
                    vids.joinToString(" + ") { "${Sessions.shortTitle(it.title)} ${Fmt.minutes(it.durationSec)}" },
                style = T.body, color = M.c.ink2,
            )
        } else {
            Text("Niente in programma. Quando vuoi, pianifica i prossimi giorni.", style = T.body, color = M.c.ink2)
            Spacer(Modifier.height(14.dp))
            TonalButton("Apri il planner", { nav.select(Tab.PLANNER) }, icon = "edit_calendar", height = 48.dp)
        }
    }
}

@Composable
private fun FirstRunCard(modifier: Modifier) {
    val nav = LocalNav.current
    Card(modifier.fillMaxWidth()) {
        Text("Si comincia da qui", style = T.heroTitle, color = M.c.ink)
        Spacer(Modifier.height(6.dp))
        Text("Costanza, non perfezione: bastano pochi video che ti piacciono e qualche serata segnata in calendario.", style = T.body, color = M.c.ink2)
        Spacer(Modifier.height(16.dp))
        listOf("Salva i tuoi video di YouTube in Libreria", "Pianifica la settimana: quando, dove, cosa", "Avvia la sessione da qui, in un tocco").forEachIndexed { i, s ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberBadge(i + 1, 24.dp)
                Spacer(Modifier.width(12.dp))
                Text(s, style = T.body14, color = M.c.ink)
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Vai alla Libreria", { nav.select(Tab.LIBRARY) }, Modifier.fillMaxWidth(), icon = "video_library", height = 48.dp)
    }
}

@Composable
private fun WeightMini(weights: List<Pair<Long, Double>>, today: LocalDate, modifier: Modifier) {
    val nav = LocalNav.current
    val trend = remember(weights, today) { Trend.of(weights, today.toEpochDay()) }
    val enough = weights.size >= Trend.MIN_WEIGH_INS
    val now = if (enough) trend.at(today.toEpochDay()) else weights.lastOrNull()?.second
    val weekly = if (enough) trend.change(today.toEpochDay(), 7) else null
    val spark = remember(trend, today) {
        if (!enough) emptyList() else (27 downTo 0).mapNotNull { trend.at(today.toEpochDay() - it).takeIf { _ -> today.toEpochDay() - it >= trend.first } }
    }
    Card(modifier, radius = 24.dp, padding = PaddingValues(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 14.dp), onClick = { nav.select(Tab.WEIGHT) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (enough) "Peso · trend" else "Peso", style = T.secondary, color = M.c.ink2, modifier = Modifier.weight(1f))
            RoundIconButton("add", { nav.sheet = SheetKind.Weigh() }, size = 32.dp, iconSize = 20.dp, bg = M.c.accentSoft, fg = M.c.accentInk, contentDescription = "Nuova pesata")
        }
        Spacer(Modifier.height(4.dp))
        if (now != null) StatValue(Fmt.kg(now), "kg", ts(30, FontWeight.SemiBold, (-0.02).em))
        else Text("—", style = ts(30, FontWeight.SemiBold), color = M.c.ink3)
        Text(
            when {
                weekly != null -> "${Fmt.signedKg(weekly)} kg/sett."
                weights.isEmpty() -> "Nessuna pesata"
                else -> "Ancora ${Trend.MIN_WEIGH_INS - weights.size} per il trend"
            },
            style = T.caption, color = M.c.ink3,
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(8.dp))
        if (spark.size >= 2) Sparkline(spark, Modifier.width(124.dp).height(40.dp))
        else Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun StepsMini(steps: StepsState, goal: Int, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(Health.permissionContract()) { scope.launch { Health.refresh(context) } }
    LaunchedEffect(Unit) { Health.refresh(context) }
    Card(modifier, radius = 24.dp, padding = PaddingValues(16.dp)) {
        Text("Passi oggi", style = T.secondary, color = M.c.ink2)
        Spacer(Modifier.height(10.dp))
        when {
            steps.granted -> {
                Text(Fmt.thousands(steps.today ?: 0), style = ts(30, FontWeight.SemiBold), color = M.c.ink)
                Text("di ${Fmt.thousands(goal)} · Health Connect", style = T.caption, color = M.c.ink3)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(14.dp))
                GrowBar((steps.today ?: 0).toFloat() / goal, Modifier.fillMaxWidth())
            }
            steps.available -> {
                Text("Collega Health Connect per vedere i passi.", style = T.secondary, color = M.c.ink2)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(10.dp))
                TonalButton("Collega", { launcher.launch(Health.requestSet) }, height = 40.dp)
            }
            else -> {
                Text("Health Connect non è disponibile su questo telefono.", style = T.secondary, color = M.c.ink2)
            }
        }
    }
}
