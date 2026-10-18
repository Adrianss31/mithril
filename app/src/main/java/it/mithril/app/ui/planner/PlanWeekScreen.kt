package it.mithril.app.ui.planner

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mithril.app.ui.components.dashedBorder
import it.mithril.app.ui.rememberRepo
import it.mithril.app.ui.statusBarTop
import it.mithril.app.ui.theme.rise
import it.mithril.app.ui.theme.ts
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt
import it.mithril.app.data.SlotEntity
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Sessions
import it.mithril.app.domain.Trend
import it.mithril.app.domain.Week
import it.mithril.app.domain.WeekSummary
import it.mithril.app.health.Health
import it.mithril.app.health.StepsState
import it.mithril.app.reminders.Reminders
import it.mithril.app.ui.LocalNav
import it.mithril.app.ui.SheetKind
import it.mithril.app.ui.components.Card
import it.mithril.app.ui.components.GrowBar
import it.mithril.app.ui.components.LocalToast
import it.mithril.app.ui.components.OutlineButton
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.components.ReviewDays
import it.mithril.app.ui.components.RoundIconButton
import it.mithril.app.ui.components.Sparkline
import it.mithril.app.ui.components.StepDashes
import it.mithril.app.ui.components.Symbol
import it.mithril.app.ui.theme.Ease
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T

private data class DayPlan(val videos: List<Long>, val time: Int, val place: String)

/** Flusso guidato "Pianifica la settimana" in 4 passi per la settimana che inizia il lunedì [start]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanWeekScreen(start: LocalDate) {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val today by repo.today.collectAsStateWithLifecycle()
    val slots by repo.slots.collectAsStateWithLifecycle()
    val logs by repo.logs.collectAsStateWithLifecycle()
    val videos by repo.videos.collectAsStateWithLifecycle()
    val weights by repo.weights.collectAsStateWithLifecycle()
    val settings by repo.appSettings.collectAsStateWithLifecycle()
    val steps by Health.state.collectAsStateWithLifecycle()
    val all = videos ?: emptyList()
    val prev = start.minusWeeks(1)
    val goals = remember { repo.goals() }
    val prevSummary = remember(slots, logs) { Week.summary(prev, today, slots, logs, Week.goalFor(prev, goals)) }
    var step by remember { mutableIntStateOf(0) }
    var goal by remember { mutableIntStateOf(Week.goalFor(start, goals)) }
    val plan = remember { mutableStateMapOf<Int, DayPlan>() }
    var loaded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Long?>(null) }
    val firstEditable = if (start.isBefore(today)) (today.toEpochDay() - start.toEpochDay()).toInt().coerceIn(0, 7) else 0
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(start) {
        val from = start.toEpochDay()
        val existing = repo.dao.slotsBetween(from, from + 6)
        for (i in 0..6) {
            val day = start.plusDays(i.toLong())
            val s = existing.filter { it.date == day.toEpochDay() }.minByOrNull { it.timeMin }
            val like = s ?: repo.lastSlotLike(day)
            plan[i] = DayPlan(s?.videoIdList ?: emptyList(), like?.timeMin ?: (18 * 60 + 30), like?.place ?: "Soggiorno")
        }
        loaded = true
    }

    fun assign(day: Int, id: Long) {
        if (day < firstEditable) { toast.show("Questo giorno è già passato"); return }
        val p = plan[day] ?: return
        if (id in p.videos) return
        if (p.videos.size >= 3) { toast.show("Massimo 3 video per giorno"); return }
        plan[day] = p.copy(videos = p.videos + id)
        selected = null
    }
    val count = plan.count { it.key >= firstEditable && it.value.videos.isNotEmpty() } + (0 until firstEditable).count { i -> slots.any { it.date == start.plusDays(i.toLong()).toEpochDay() } }

    // Trascinamento
    val dayRects = remember { mutableStateMapOf<Int, Rect>() }
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    val chipOrigins = remember { mutableStateMapOf<Long, Offset>() }
    val hoverDay = dragId?.let { dayRects.entries.firstOrNull { e -> e.value.contains(dragPos) }?.key }

    Box(Modifier.fillMaxSize().background(M.c.bg).onGloballyPositioned { rootPos = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 12.dp, end = 20.dp, top = statusBarTop() + 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundIconButton("close", { nav.pop() }, size = 44.dp, bg = M.c.bg, contentDescription = "Chiudi")
                    Text("Pianifica la settimana", style = T.title16, color = M.c.ink, modifier = Modifier.weight(1f))
                    Text("${step + 1} di 4", style = T.secondary, color = M.c.ink2)
                }
                StepDashes(4, step, Modifier.padding(start = 8.dp, top = 6.dp))
            }
            val motion = M.motion
            AnimatedContent(
                step,
                transitionSpec = {
                    if (!motion) EnterTransition.None togetherWith ExitTransition.None
                    else (fadeIn(tween(400, easing = Ease.Standard)) + slideInHorizontally(tween(400, easing = Ease.Standard)) { 60 }) togetherWith fadeOut(tween(120))
                },
                modifier = Modifier.weight(1f),
                label = "flow",
            ) { st ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = dragId == null).padding(horizontal = 20.dp, vertical = 20.dp)) {
                    when (st) {
                        0 -> StepHowItWent(prev, prevSummary, weights?.map { it.date to it.kg } ?: emptyList(), today, steps, settings.stepsGoal)
                        1 -> StepGoal(goal, prevSummary.sessions, { goal = (goal - 1).coerceAtLeast(1) }, { goal = (goal + 1).coerceAtMost(7) })
                        2 -> {
                            Text("Metti i video sui giorni", style = T.screenTitle, color = M.c.ink)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (selected != null) "Ora tocca un giorno" else "Trascina un video su un giorno, oppure toccalo e poi tocca il giorno.",
                                style = T.body, color = M.c.ink2,
                            )
                            Spacer(Modifier.height(16.dp))
                            if (all.isEmpty()) Text("La Libreria è vuota: salva prima qualche video da YouTube.", style = T.body14, color = M.c.ink3)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                all.forEach { v ->
                                    val sel = selected == v.id
                                    val bg by animateColorAsState(if (sel) M.c.accentSoft else M.c.surface, tween(250), label = "vchip")
                                    Row(
                                        Modifier
                                            .rise(0, Unit, baseDelay = 120, ms = 450)
                                            .onGloballyPositioned { chipOrigins[v.id] = it.positionInRoot() }
                                            .height(40.dp)
                                            .widthIn(max = 300.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(bg)
                                            .border(1.dp, if (sel) M.c.accentSoft else M.c.line2, RoundedCornerShape(20.dp))
                                            .pointerInput(v.id) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { o -> dragId = v.id; dragPos = (chipOrigins[v.id] ?: Offset.Zero) + o },
                                                    onDrag = { ch, d -> ch.consume(); dragPos += d },
                                                    onDragEnd = {
                                                        val target = dayRects.entries.firstOrNull { it.value.contains(dragPos) }?.key
                                                        if (target != null) assign(target, v.id)
                                                        dragId = null
                                                    },
                                                    onDragCancel = { dragId = null },
                                                )
                                            }
                                            .clickable { selected = if (sel) null else v.id }
                                            .padding(start = 8.dp, end = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Symbol("drag_indicator", 16.dp, (if (sel) M.c.accentInk else M.c.ink).copy(alpha = 0.5f))
                                        Spacer(Modifier.width(4.dp))
                                        Text(v.title, style = T.button15.copy(fontSize = 14.sp), color = if (sel) M.c.accentInk else M.c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            Row(Modifier.padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("$count di $goal sessioni", style = T.body14.copy(fontWeight = FontWeight.SemiBold), color = M.c.ink, modifier = Modifier.weight(1f))
                                Row(
                                    Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                                        val pf = prev.toEpochDay()
                                        val old = slots.filter { it.date in pf..pf + 6 }
                                        if (old.isEmpty()) toast.show("La settimana scorsa era vuota")
                                        for (i in firstEditable..6) {
                                            val o = old.filter { it.date == pf + i }.minByOrNull { it.timeMin } ?: continue
                                            plan[i] = DayPlan(o.videoIdList.filter { id -> all.any { it.id == id } }.take(3), o.timeMin, o.place)
                                        }
                                    }.padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Symbol("content_copy", 16.dp, M.c.accent)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copia la settimana scorsa", style = T.button15.copy(fontSize = 14.sp), color = M.c.accent)
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (i in 0..6) {
                                    val day = start.plusDays(i.toLong())
                                    val p = plan[i]
                                    val past = i < firstEditable
                                    val target = (selected != null || dragId != null) && !past
                                    val shape = RoundedCornerShape(18.dp)
                                    Column(
                                        Modifier
                                            .rise(i, Unit, baseDelay = 120, ms = 450)
                                            .fillMaxWidth()
                                            .onGloballyPositioned { dayRects[i] = it.boundsInRoot() }
                                            .then(if (!M.c.dark) Modifier.shadow(4.dp, shape, ambientColor = androidx.compose.ui.graphics.Color(0x183C281E), spotColor = androidx.compose.ui.graphics.Color(0x183C281E)) else Modifier)
                                            .clip(shape)
                                            .background(if (hoverDay == i) M.c.accentSoft else M.c.surface)
                                            .then(if (target) Modifier.dashedBorder(M.c.accent, 18.dp, 1.5.dp) else Modifier)
                                            .clickable(enabled = selected != null && !past) { selected?.let { assign(i, it) } }
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(Fmt.dayName(day.dayOfWeek), style = ts(15, FontWeight.Bold), color = if (past) M.c.ink3 else M.c.ink, modifier = Modifier.weight(1f))
                                            Text(Fmt.dateShort(day), style = T.caption, color = M.c.ink2)
                                        }
                                        val ids = if (past) slots.filter { it.date == day.toEpochDay() }.flatMap { it.videoIdList } else p?.videos ?: emptyList()
                                        if (ids.isEmpty()) Text(if (past) "passato" else "riposo", style = T.secondary, color = M.c.ink3, modifier = Modifier.padding(top = 4.dp))
                                        else FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            ids.forEachIndexed { k, id ->
                                                val v = all.firstOrNull { it.id == id } ?: return@forEachIndexed
                                                Row(
                                                    Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(M.c.accentSoft).padding(start = 12.dp, end = if (past) 12.dp else 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text(v.title, style = ts(13, FontWeight.SemiBold), color = M.c.accentInk, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
                                                    if (!past) Box(Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).clickable {
                                                        plan[i] = p!!.copy(videos = p.videos.filterIndexed { j, _ -> j != k })
                                                    }, contentAlignment = Alignment.Center) { Symbol("close", 16.dp, M.c.accentInk) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            Text("Le tue intenzioni", style = T.screenTitle, color = M.c.ink)
                            Spacer(Modifier.height(6.dp))
                            Text("Quando, dove, cosa. Ti ricordo tutto ${settings.reminderLeadMin} minuti prima. Tocca una frase per cambiare ora o luogo.", style = T.body, color = M.c.ink2)
                            Spacer(Modifier.height(18.dp))
                            val days = (firstEditable..6).filter { plan[it]?.videos?.isNotEmpty() == true }
                            if (days.isEmpty()) Text("Nessun video sui giorni: va bene anche una settimana leggera, ma prova almeno con una sessione mini.", style = T.body, color = M.c.ink2)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                days.forEachIndexed { k, i ->
                                    val p = plan[i]!!
                                    val day = start.plusDays(i.toLong())
                                    Card(Modifier.rise(k, Unit, baseDelay = 240), radius = 20.dp, padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp), onClick = {
                                        nav.sheet = SheetKind.NewSlot(day, edit = SlotEntity(0, day.toEpochDay(), p.time, p.place, ""), onlyWhenWhere = true) { t, pl ->
                                            plan[i] = plan[i]!!.copy(time = t, place = pl)
                                        }
                                    }) {
                                        val titles = Sessions.videosOf(p.videos, all).map { it.title }
                                        Text(buildAnnotatedString {
                                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(Fmt.dayName(day.dayOfWeek)) }
                                            append(" alle ${Fmt.time(p.time)}, ${Places.phrase(p.place)}: ${titles.joinToString(" + ")}.")
                                        }, style = ts(16, lineHeight = (16 * 1.45).sp), color = M.c.ink)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Footer fisso
            Row(
                Modifier.fillMaxWidth().background(M.c.bg).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlineButton(if (step == 0) "Annulla" else "Indietro", { if (step == 0) nav.pop() else step-- }, Modifier.weight(0.8f), height = 54.dp)
                PrimaryButton(
                    if (step == 3) "Conferma" else "Avanti",
                    {
                        if (step < 3) step++
                        else scope.launch {
                            val newSlots = (firstEditable..6).mapNotNull { i ->
                                val p = plan[i] ?: return@mapNotNull null
                                if (p.videos.isEmpty()) null
                                else SlotEntity(0, start.plusDays(i.toLong()).toEpochDay(), p.time, p.place, p.videos.joinToString(","))
                            }
                            val from = start.plusDays(firstEditable.toLong())
                            repo.dao.replaceWeekSlots(from.toEpochDay(), start.toEpochDay() + 6, newSlots)
                            repo.setWeekGoal(start, goal)
                            if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context)) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            toast.show("Settimana pianificata · ${if (count == 1) "1 sessione" else "$count sessioni"}")
                            nav.pop()
                        }
                    },
                    Modifier.weight(1.4f),
                    enabled = loaded,
                )
            }
        }
        // Fantasma del chip trascinato
        val dragged = dragId?.let { id -> all.firstOrNull { it.id == id } }
        if (dragged != null) {
            val density = LocalDensity.current
            Row(
                Modifier
                    .offset { IntOffset((dragPos.x - rootPos.x - with(density) { 30.dp.toPx() }).roundToInt(), (dragPos.y - rootPos.y - with(density) { 20.dp.toPx() }).roundToInt()) }
                    .shadow(10.dp, RoundedCornerShape(20.dp))
                    .height(40.dp)
                    .widthIn(max = 260.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(M.c.accent)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Text(dragged.title, style = T.button15.copy(fontSize = 14.sp), color = M.c.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun StepHowItWent(
    week: LocalDate,
    s: WeekSummary,
    weights: List<Pair<Long, Double>>,
    today: LocalDate,
    steps: StepsState,
    stepsGoal: Int,
) {
    Text("Com'è andata", style = T.screenTitle, color = M.c.ink)
    Text(Fmt.weekRange(week), style = T.secondary, color = M.c.ink2)
    Spacer(Modifier.height(16.dp))
    Card(Modifier.rise(0).fillMaxWidth()) {
        Text("Sessioni", style = T.secondary, color = M.c.ink2)
        Text(
            "${s.sessions} di ${s.goal} sessioni" + if (s.mini > 0) " · ${s.mini} mini" else "",
            style = T.sheetTitle, color = M.c.ink,
        )
        Spacer(Modifier.height(14.dp))
        ReviewDays(s.days)
    }
    Spacer(Modifier.height(12.dp))
    WeightAndSteps(week, weights, today, steps, stepsGoal, Modifier.rise(1))
    Spacer(Modifier.height(18.dp))
    Text(if (s.active) "Settimana attiva ✓" else "Ogni sessione conta.", style = T.cardTitle, color = M.c.ink, modifier = Modifier.rise(2))
}

@Composable
fun WeightAndSteps(week: LocalDate, weights: List<Pair<Long, Double>>, today: LocalDate, steps: StepsState, stepsGoal: Int, modifier: Modifier = Modifier, spark: Boolean = false) {
    val end = minOf(week.plusDays(6), today)
    val trend = remember(weights, today) { Trend.of(weights, today.toEpochDay()) }
    val enough = weights.size >= Trend.MIN_WEIGH_INS
    val tEnd = if (enough) trend.at(end.toEpochDay()) else null
    val tStart = if (enough) trend.at(week.toEpochDay() - 1) else null
    Row(modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.weight(1f).fillMaxSize(), radius = 22.dp, padding = PaddingValues(16.dp)) {
            Text("Peso · trend", style = T.secondary, color = M.c.ink2)
            Spacer(Modifier.height(4.dp))
            Text(tEnd?.let { "${Fmt.kg(it)} kg" } ?: "—", style = ts(24, FontWeight.SemiBold), color = M.c.ink)
            Text(
                if (tEnd != null && tStart != null) "${Fmt.signedKg(tEnd - tStart)} kg in settimana" else "Servono 3 pesate",
                style = T.caption, color = M.c.ink3,
            )
            if (spark && enough) {
                Spacer(Modifier.height(10.dp))
                val vals = (0..6).mapNotNull { trend.at(week.toEpochDay() + it) }
                Sparkline(vals, Modifier.fillMaxWidth().height(36.dp))
            }
        }
        Card(Modifier.weight(1f).fillMaxSize(), radius = 22.dp, padding = PaddingValues(16.dp)) {
            Text("Passi · media", style = T.secondary, color = M.c.ink2)
            Spacer(Modifier.height(4.dp))
            val avg = steps.average(week, week.plusDays(6))
            Text(avg?.let { Fmt.thousands(it) } ?: "—", style = ts(24, FontWeight.SemiBold), color = M.c.ink)
            Text(if (avg != null) "al giorno · obiettivo ${Fmt.thousands(stepsGoal)}" else "Health Connect non collegato", style = T.caption, color = M.c.ink3)
            if (spark && avg != null) {
                Spacer(Modifier.height(14.dp))
                GrowBar(avg.toFloat() / stepsGoal, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StepGoal(goal: Int, last: Int, dec: () -> Unit, inc: () -> Unit) {
    Text("Quante sessioni?", style = T.screenTitle, color = M.c.ink)
    Spacer(Modifier.height(6.dp))
    Text("La settimana scorsa: $last. Meglio un obiettivo che rispetti volentieri.", style = T.body, color = M.c.ink2)
    Spacer(Modifier.height(40.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton("remove", dec, size = 64.dp, iconSize = 30.dp, enabled = goal > 1, contentDescription = "Meno")
        AnimatedContent(goal, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "goal") { g ->
            Text("$g", style = T.display, color = M.c.ink, modifier = Modifier.widthIn(min = 140.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        RoundIconButton("add", inc, size = 64.dp, iconSize = 30.dp, enabled = goal < 7, contentDescription = "Più")
    }
    Spacer(Modifier.height(8.dp))
    Text(if (goal == 1) "sessione" else "sessioni", style = T.body, color = M.c.ink2, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}
