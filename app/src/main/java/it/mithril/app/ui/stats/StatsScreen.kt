package it.mithril.app.ui.stats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import it.mithril.app.data.SessionLogEntity
import it.mithril.app.data.SlotEntity
import it.mithril.app.data.VideoEntity
import it.mithril.app.domain.DayState
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Stats
import it.mithril.app.domain.StatsPeriod
import it.mithril.app.domain.StatsSummary
import it.mithril.app.domain.Trend
import it.mithril.app.domain.Week
import it.mithril.app.domain.WeekBar
import it.mithril.app.health.Health
import it.mithril.app.health.StepsState
import it.mithril.app.ui.BottomGap
import it.mithril.app.ui.LocalNav
import it.mithril.app.ui.Tab
import it.mithril.app.ui.components.Card
import it.mithril.app.ui.components.RoundIconButton
import it.mithril.app.ui.components.Symbol
import it.mithril.app.ui.components.TextLink
import it.mithril.app.ui.components.TonalButton
import it.mithril.app.ui.components.dashedBorder
import it.mithril.app.ui.rememberRepo
import it.mithril.app.ui.statusBarTop
import it.mithril.app.ui.theme.Ease
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T
import it.mithril.app.ui.theme.rise
import it.mithril.app.ui.theme.ts
import it.mithril.app.ui.weight.Range
import it.mithril.app.ui.weight.WeightChart
import it.mithril.app.widget.formatSteps

private enum class CalMode(val label: String) { ALL("Tutto"), STEPS("Passi"), WEIGHT("Peso") }

/** Pannello Statistiche: numeri del periodo, calendario con sessioni, passi e peso, grafici e abitudini. */
@Composable
fun StatsScreen() {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today by repo.today.collectAsStateWithLifecycle()
    val slots by repo.slots.collectAsStateWithLifecycle()
    val logs by repo.logs.collectAsStateWithLifecycle()
    val videos by repo.videos.collectAsStateWithLifecycle()
    val weightsOrNull by repo.weights.collectAsStateWithLifecycle()
    val plans by repo.weekPlans.collectAsStateWithLifecycle()
    val settings by repo.appSettings.collectAsStateWithLifecycle()
    val health by Health.state.collectAsStateWithLifecycle()
    val epoch = nav.tabEpoch

    var period by rememberSaveable { mutableStateOf(StatsPeriod.W4) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today)) }
    var selectedDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var calMode by rememberSaveable { mutableStateOf(CalMode.ALL) }
    var weekMinutes by rememberSaveable { mutableStateOf(false) }

    val goals = remember(plans) { plans.associate { it.weekStart to it.goalSessions } }
    val weights = remember(weightsOrNull) { weightsOrNull?.map { it.date to it.kg } ?: emptyList() }
    val trend = remember(weights, today) { Trend.of(weights, today.toEpochDay()) }
    val from = remember(period, today, logs, weights, slots) { Stats.from(period, today, logs, weights, slots) }

    // Passi: lo storico letto per il periodo e il mese del calendario, più quelli freschi dell'ultimo aggiornamento.
    var history by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var loadedFrom by remember { mutableStateOf<LocalDate?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val need = minOf(from, month.atDay(1))
    LaunchedEffect(health.granted, health.historyGranted, need, reload) {
        if (!health.granted) return@LaunchedEffect
        val lf = loadedFrom
        if (lf != null && !need.isBefore(lf)) return@LaunchedEffect
        history = history + Health.stepsBetween(context, need, lf?.minusDays(1) ?: today)
        loadedFrom = need
    }
    val steps = remember(history, health.byDay) { history + health.byDay }
    val healthLauncher = rememberLauncherForActivityResult(Health.permissionContract()) {
        scope.launch { Health.refresh(context); loadedFrom = null; history = emptyMap(); reload++ }
    }

    val s = remember(from, today, slots, logs, goals, weights, steps, settings.stepsGoal) {
        Stats.summary(from, today, slots, logs, goals, weights, steps, settings.stepsGoal)
    }
    val all = videos ?: emptyList()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = statusBarTop() + 14.dp)) {
        Text("Statistiche", style = T.screenTitle, color = M.c.ink, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(14.dp))
        Segmented(StatsPeriod.entries, period, { it.label }, { period = it })
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Kpis(s, period, health, Modifier.rise(0, epoch))
            CalendarCard(
                month, { month = it }, today, selectedDay, { selectedDay = it }, calMode, { calMode = it },
                slots, logs, weights, trend, steps, settings.stepsGoal, all, Modifier.rise(1, epoch),
            )
            WeeksCard(s, weekMinutes, { weekMinutes = it }, Modifier.rise(2, epoch))
            StepsCard(s, steps, settings.stepsGoal, health, period, { healthLauncher.launch(Health.requestSet) }, Modifier.rise(3, epoch))
            if (weights.size >= Trend.MIN_WEIGH_INS) {
                Card(Modifier.rise(4, epoch).fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
                    CardHeader("Peso", s.trendChange?.let { "trend ${Fmt.signedKg(it)} kg nel periodo" })
                    Spacer(Modifier.height(14.dp))
                    val range = when (period) { StatsPeriod.W4 -> Range.M1; StatsPeriod.M3 -> Range.M3; else -> Range.Y1 }
                    WeightChart(trend, today, range, settings.goalKg, trend.dailySlope(today.toEpochDay()), Modifier.fillMaxWidth().height(170.dp))
                    Spacer(Modifier.height(10.dp))
                    TextLink("Dettagli nella scheda Peso", { nav.select(Tab.WEIGHT) })
                }
            }
            HabitsCard(s, Modifier.rise(5, epoch))
            if (s.topVideos.isNotEmpty()) TopVideosCard(s.topVideos, all, Modifier.rise(6, epoch))
            Spacer(Modifier.height(BottomGap))
        }
    }
}

@Composable
private fun <E> Segmented(items: List<E>, selected: E, label: (E) -> String, onSelect: (E) -> Unit, height: Int = 40) {
    Row(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape((height / 2).dp)).background(M.c.surface2).padding(3.dp)) {
        items.forEach { r ->
            val sel = r == selected
            val bg by animateColorAsState(if (sel) M.c.surface else M.c.surface2, tween(250), label = "seg")
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .then(if (sel && !M.c.dark) Modifier.shadow(2.dp, RoundedCornerShape(((height - 6) / 2).dp), ambientColor = Color(0x223C281E), spotColor = Color(0x223C281E)) else Modifier)
                    .clip(RoundedCornerShape(((height - 6) / 2).dp)).background(bg).clickable { onSelect(r) },
                contentAlignment = Alignment.Center,
            ) { Text(label(r), style = ts(13, if (sel) FontWeight.SemiBold else FontWeight.Medium), color = if (sel) M.c.ink else M.c.ink2, maxLines = 1) }
        }
    }
}

@Composable
private fun CardHeader(title: String, sub: String?) {
    Text(title, style = T.cardTitle, color = M.c.ink)
    if (sub != null) Text(sub, style = T.secondary, color = M.c.ink2)
}

// --- Numeri principali ---

@Composable
private fun Kpis(s: StatsSummary, period: StatsPeriod, health: StepsState, modifier: Modifier) {
    val days = (s.to.toEpochDay() - s.from.toEpochDay() + 1).toInt()
    val weeks = (days / 7.0).coerceAtLeast(1.0)
    val total = s.sessions + s.mini
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KpiRow(
            Kpi("Sessioni", "$total", if (s.mini > 0) "di cui ${s.mini} mini · ${s.trainingDays} giorni" else "in ${s.trainingDays} ${if (s.trainingDays == 1) "giorno" else "giorni"}"),
            Kpi("Minuti", Fmt.thousands(s.minutes), "≈ ${(s.minutes / weeks).toInt()} a settimana"),
        )
        KpiRow(
            Kpi("Settimane attive", "${s.activeWeeks}/${s.weeks.size}", "serie ${s.streak} · record ${s.bestStreak}", icon = "local_fire_department"),
            Kpi(
                "Passi · media", s.stepsAvg?.let { Fmt.thousands(it) } ?: "—",
                when {
                    s.stepsAvg != null -> "obiettivo in ${s.stepsGoalDays} giorni su ${s.stepsDays}"
                    !health.granted -> "Health Connect non collegato"
                    else -> "nessun dato nel periodo"
                },
            ),
        )
        KpiRow(
            Kpi("Peso · trend", s.trendChange?.let { "${Fmt.signedKg(it)} kg" } ?: "—",
                if (s.trendStart != null && s.trendEnd != null) "da ${Fmt.kg(s.trendStart)} a ${Fmt.kg(s.trendEnd)}" else "servono ${Trend.MIN_WEIGH_INS} pesate"),
            Kpi("Pesate", "${s.weighIns}", if (s.weighIns > 1) "una ogni ${Fmt.kg(days.toDouble() / s.weighIns).removeSuffix(",0")} giorni" else if (period == StatsPeriod.ALL) "in totale" else "nel periodo"),
        )
    }
}

private data class Kpi(val label: String, val value: String, val sub: String, val icon: String? = null)

@Composable
private fun KpiRow(a: Kpi, b: Kpi) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(a, b).forEach { k ->
            Card(Modifier.weight(1f).fillMaxHeight(), radius = 22.dp, padding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(k.label, style = T.secondary, color = M.c.ink2, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (k.icon != null) Symbol(k.icon, 16.dp, M.c.accent, filled = true)
                }
                Spacer(Modifier.height(2.dp))
                Text(k.value, style = ts(24, FontWeight.SemiBold), color = M.c.ink, maxLines = 1)
                Text(k.sub, style = T.caption, color = M.c.ink3)
            }
        }
    }
}

// --- Calendario ---

@Composable
private fun CalendarCard(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    today: LocalDate,
    selected: Long,
    onSelect: (Long) -> Unit,
    mode: CalMode,
    onMode: (CalMode) -> Unit,
    slots: List<SlotEntity>,
    logs: List<SessionLogEntity>,
    weights: List<Pair<Long, Double>>,
    trend: Trend,
    steps: Map<Long, Int>,
    stepsGoal: Int,
    videos: List<VideoEntity>,
    modifier: Modifier,
) {
    val c = M.c
    val weighIns = remember(weights) { weights.toMap() }
    val first = remember(logs, weights, slots) { listOfNotNull(logs.minOfOrNull { it.date }, weights.minOfOrNull { it.first }, slots.minOfOrNull { it.date }).minOrNull() }
    val canBack = first == null || month.atDay(1).toEpochDay() > first
    val canNext = month < YearMonth.from(today)
    Card(modifier.fillMaxWidth(), padding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton("chevron_left", { onMonth(month.minusMonths(1)) }, size = 40.dp, bg = Color.Transparent, enabled = canBack, contentDescription = "Mese precedente")
            Text(
                "${Fmt.month(month.atDay(1)).replaceFirstChar { it.uppercase() }} ${month.year}",
                style = T.cardTitle, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            RoundIconButton("chevron_right", { onMonth(month.plusMonths(1)) }, size = 40.dp, bg = Color.Transparent, enabled = canNext, contentDescription = "Mese successivo")
        }
        Spacer(Modifier.height(8.dp))
        Segmented(CalMode.entries, mode, { it.label }, onMode, height = 34)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            java.time.DayOfWeek.entries.forEach { d ->
                Text(Fmt.dayLetter(d), style = T.captionBold, color = c.ink3, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        val start = month.atDay(1)
        val lead = start.dayOfWeek.value - 1
        val cells = lead + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (k in 0..6) {
                        val i = r * 7 + k - lead
                        if (i < 0 || i >= month.lengthOfMonth()) { Spacer(Modifier.weight(1f)); continue }
                        val date = start.plusDays(i.toLong())
                        val d = date.toEpochDay()
                        DayCell(
                            date, Week.dayState(date, today, slots, logs), date == today, d == selected, date.isAfter(today),
                            mode, steps[d]?.takeIf { it > 0 }, stepsGoal, weighIns[d], Modifier.weight(1f),
                        ) { onSelect(d) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        CalendarLegend(mode)
        Spacer(Modifier.height(14.dp))
        DayDetail(LocalDate.ofEpochDay(selected), today, slots, logs, weighIns[selected], trend, steps[selected]?.takeIf { it > 0 }, stepsGoal, videos)
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    state: DayState,
    isToday: Boolean,
    isSelected: Boolean,
    future: Boolean,
    mode: CalMode,
    steps: Int?,
    stepsGoal: Int,
    kg: Double?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = M.c
    val shape = RoundedCornerShape(12.dp)
    val stepsF = steps?.let { (it.toFloat() / stepsGoal).coerceIn(0f, 1f) } ?: 0f
    val bg = when (mode) {
        CalMode.ALL -> when (state) { DayState.DONE -> c.accent; DayState.MINI -> c.accentSoft; else -> Color.Transparent }
        CalMode.STEPS -> if (steps != null) c.accent.copy(alpha = 0.10f + 0.55f * stepsF) else Color.Transparent
        CalMode.WEIGHT -> if (kg != null) c.surface2 else Color.Transparent
    }
    val strong = mode == CalMode.ALL && state == DayState.DONE || mode == CalMode.STEPS && stepsF >= 1f
    val ink = when {
        strong -> c.onAccent
        mode == CalMode.ALL && state == DayState.MINI -> c.accentInk
        future -> c.ink3
        else -> c.ink
    }
    Box(
        modifier
            .aspectRatio(0.82f)
            .clip(shape)
            .background(bg)
            .then(
                when {
                    isSelected -> Modifier.border(2.dp, c.ink, shape)
                    mode == CalMode.ALL && state == DayState.SKIPPED -> Modifier.dashedBorder(c.ink3, 12.dp, 1.dp)
                    mode == CalMode.ALL && state == DayState.PLANNED -> Modifier.border(1.dp, c.line2, shape)
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            "${date.dayOfMonth}", style = ts(14, if (isToday) FontWeight.Bold else FontWeight.Medium), color = ink,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
        )
        if (isToday) Box(Modifier.align(Alignment.TopCenter).padding(top = 24.dp).size(width = 12.dp, height = 2.dp).clip(RoundedCornerShape(1.dp)).background(ink))
        when (mode) {
            CalMode.ALL -> {
                if (kg != null) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(5.dp).clip(CircleShape).background(if (strong) c.onAccent else c.ink2))
                if (steps != null) {
                    val track = if (strong) c.onAccent.copy(alpha = 0.3f) else c.line2
                    val fill = if (strong) c.onAccent else if (stepsF >= 1f) c.accent else c.ink2
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp).fillMaxWidth(0.62f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(track)) {
                        Box(Modifier.fillMaxWidth(stepsF).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(fill))
                    }
                }
            }
            CalMode.STEPS -> if (steps != null) Text(formatSteps(steps), style = ts(10, FontWeight.SemiBold), color = ink, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), maxLines = 1)
            CalMode.WEIGHT -> if (kg != null) Text(Fmt.kg(kg), style = ts(10, FontWeight.SemiBold), color = c.ink, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), maxLines = 1)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CalendarLegend(mode: CalMode) {
    val c = M.c
    @Composable
    fun item(label: String, swatch: @Composable () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
        swatch(); Spacer(Modifier.width(5.dp)); Text(label, style = T.caption, color = c.ink2)
    }
    val box = Modifier.size(12.dp).clip(RoundedCornerShape(4.dp))
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (mode) {
            CalMode.ALL -> {
                item("sessione") { Box(box.background(c.accent)) }
                item("mini") { Box(box.background(c.accentSoft)) }
                item("saltata") { Box(box.dashedBorder(c.ink3, 4.dp, 1.dp)) }
                item("passi") { Box(Modifier.width(14.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.ink2)) }
                item("pesata") { Box(Modifier.size(5.dp).clip(CircleShape).background(c.ink2)) }
            }
            CalMode.STEPS -> {
                item("pochi passi") { Box(box.background(c.accent.copy(alpha = 0.15f))) }
                item("obiettivo raggiunto") { Box(box.background(c.accent.copy(alpha = 0.65f))) }
            }
            CalMode.WEIGHT -> item("giorno con pesata, in kg") { Box(box.background(c.surface2)) }
        }
    }
}

@Composable
private fun DayDetail(
    date: LocalDate,
    today: LocalDate,
    slots: List<SlotEntity>,
    logs: List<SessionLogEntity>,
    kg: Double?,
    trend: Trend,
    steps: Int?,
    stepsGoal: Int,
    videos: List<VideoEntity>,
) {
    val c = M.c
    val d = date.toEpochDay()
    val dayLogs = logs.filter { it.date == d }
    val state = Week.dayState(date, today, slots, logs)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface2).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(Fmt.dayLong(date), style = T.title15, color = c.ink)
        DetailRow("fitness_center") {
            if (dayLogs.isEmpty()) {
                val planned = slots.filter { it.date == d }.flatMap { it.videoIdList }.mapNotNull { id -> videos.firstOrNull { it.id == id }?.title }
                Text(
                    when (state) {
                        DayState.SKIPPED -> "Sessione saltata" + if (planned.isNotEmpty()) ": ${planned.joinToString(" + ")}" else ""
                        DayState.PLANNED -> "In programma: ${planned.joinToString(" + ")}"
                        else -> "Riposo"
                    },
                    style = T.body14, color = c.ink2,
                )
            } else Column {
                dayLogs.forEach { l ->
                    Text(l.title, style = T.body14.copy(fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(if (l.isMini) "mini" else null, "${Stats.minutes(l)} min", l.fatigue?.let { "fatica $it/10" }, l.note).joinToString(" · "),
                        style = T.caption, color = c.ink2,
                    )
                }
            }
        }
        DetailRow("directions_walk") {
            Text(
                if (steps != null) "${Fmt.thousands(steps)} passi" + if (steps >= stepsGoal) " · obiettivo raggiunto" else " · ${(steps * 100 / stepsGoal)}% dell'obiettivo"
                else "Passi non disponibili",
                style = T.body14, color = if (steps != null) c.ink else c.ink3,
            )
        }
        DetailRow("monitor_weight") {
            val tr = if (trend.weighIns.size >= Trend.MIN_WEIGH_INS && d >= trend.first) trend.at(d) else null
            Text(
                listOfNotNull(kg?.let { "pesata ${Fmt.kg(it)} kg" } ?: "nessuna pesata", tr?.let { "trend ${Fmt.kg(it)} kg" }).joinToString(" · "),
                style = T.body14, color = if (kg != null) c.ink else c.ink2,
            )
        }
    }
}

@Composable
private fun DetailRow(icon: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Symbol(icon, 18.dp, M.c.ink2, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

// --- Settimane ---

@Composable
private fun WeeksCard(s: StatsSummary, minutes: Boolean, onMinutes: (Boolean) -> Unit, modifier: Modifier) {
    Card(modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
        CardHeader("Settimana per settimana", "${s.activeWeeks} attive su ${s.weeks.size}")
        Spacer(Modifier.height(12.dp))
        Segmented(listOf(false, true), minutes, { if (it) "Minuti" else "Sessioni" }, onMinutes, height = 34)
        Spacer(Modifier.height(14.dp))
        WeekBars(s.weeks, minutes, Modifier.fillMaxWidth().height(150.dp))
        if (!minutes) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                val c = M.c
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c.accent)); Spacer(Modifier.width(5.dp)); Text("complete", style = T.caption, color = c.ink2) }
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c.accentSoft)); Spacer(Modifier.width(5.dp)); Text("mini", style = T.caption, color = c.ink2) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.width(14.dp).height(3.dp)) { drawLine(c.ink, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))) }
                    Spacer(Modifier.width(5.dp)); Text("obiettivo", style = T.caption, color = c.ink2)
                }
            }
        }
    }
}

@Composable
private fun WeekBars(weeks: List<WeekBar>, minutes: Boolean, modifier: Modifier) {
    val c = M.c
    val measurer = rememberTextMeasurer()
    val grow = remember(weeks.size, minutes) { Animatable(0f) }
    val motion = M.motion
    LaunchedEffect(weeks.size, minutes) { if (motion) grow.animateTo(1f, tween(800, 150, Ease.Standard)) else grow.snapTo(1f) }
    if (weeks.isEmpty()) return
    val values = weeks.map { if (minutes) it.minutes.toFloat() else it.sessions.toFloat() }
    val top = (values + if (minutes) emptyList() else weeks.map { it.goal.toFloat() }).maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val labelStyle = T.caption.copy(color = c.ink3, fontSize = T.caption.fontSize * 0.92f)
    val valueStyle = ts(11, FontWeight.SemiBold, color = c.ink2)
    Canvas(modifier) {
        val bottomH = 18.dp.toPx()
        val topH = 16.dp.toPx()
        val h = size.height - bottomH - topH
        val slot = size.width / weeks.size
        val bw = (slot * 0.62f).coerceAtMost(28.dp.toPx())
        fun y(v: Float) = topH + h - v / top * h
        weeks.forEachIndexed { i, w ->
            val cx = slot * i + slot / 2
            val x0 = cx - bw / 2
            val r = CornerRadius(minOf(bw / 2, 5.dp.toPx()))
            if (minutes) {
                val v = w.minutes * grow.value
                if (v > 0) drawRoundRect(c.accent, Offset(x0, y(v)), Size(bw, y(0f) - y(v)), r)
            } else {
                drawRoundRect(c.surface2, Offset(x0, topH), Size(bw, h), r)
                val full = w.full * grow.value; val all = w.sessions * grow.value
                if (all > 0) drawRoundRect(c.accentSoft, Offset(x0, y(all)), Size(bw, y(0f) - y(all)), r)
                if (full > 0) drawRoundRect(c.accent, Offset(x0, y(full)), Size(bw, y(0f) - y(full)), r)
                val gy = y(w.goal.toFloat())
                drawLine(c.ink, Offset(x0 - 2.dp.toPx(), gy), Offset(x0 + bw + 2.dp.toPx(), gy), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())))
            }
            val v = values[i]
            if (weeks.size <= 14 && v > 0) {
                val txt = measurer.measure("${v.toInt()}", valueStyle)
                drawText(txt, topLeft = Offset(cx - txt.size.width / 2f, y(v * grow.value) - txt.size.height - 1.dp.toPx()))
            }
        }
        val idx = listOf(0, weeks.size / 2, weeks.size - 1).distinct()
        idx.forEach { i ->
            val txt = measurer.measure(Fmt.dateShort(weeks[i].start), labelStyle)
            val cx = slot * i + slot / 2
            drawText(txt, topLeft = Offset((cx - txt.size.width / 2f).coerceIn(0f, size.width - txt.size.width), size.height - bottomH + 4.dp.toPx()))
        }
    }
}

// --- Passi ---

@Composable
private fun StepsCard(s: StatsSummary, steps: Map<Long, Int>, goal: Int, health: StepsState, period: StatsPeriod, onConnect: () -> Unit, modifier: Modifier) {
    val c = M.c
    Card(modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
        CardHeader("Passi al giorno", s.stepsAvg?.let { "media ${Fmt.thousands(it)} · obiettivo ${Fmt.thousands(goal)}" })
        when {
            !health.available -> Text("Health Connect non è disponibile su questo telefono.", style = T.body14, color = c.ink2, modifier = Modifier.padding(top = 8.dp))
            !health.granted -> {
                Text("Collega Health Connect per vedere i passi accanto agli allenamenti.", style = T.body14, color = c.ink2, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
                TonalButton("Collega Health Connect", onConnect, height = 44.dp)
            }
            else -> {
                Spacer(Modifier.height(14.dp))
                val days = (s.from.toEpochDay()..s.to.toEpochDay()).toList()
                StepsBars(days, steps, goal, Modifier.fillMaxWidth().height(150.dp))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c.accent)); Spacer(Modifier.width(5.dp)); Text("obiettivo raggiunto", style = T.caption, color = c.ink2) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.width(14.dp).height(2.dp).background(c.ink)); Spacer(Modifier.width(5.dp)); Text("media 7 giorni", style = T.caption, color = c.ink2) }
                }
                Spacer(Modifier.height(12.dp))
                s.stepsBest?.let { (d, n) -> Line("Giorno migliore", "${Fmt.thousands(n)} · ${Fmt.dayShort(d)}") }
                if (s.stepsTraining != null && s.stepsRest != null) {
                    Line("Nei giorni di allenamento", Fmt.thousands(s.stepsTraining))
                    Line("Nei giorni di riposo", Fmt.thousands(s.stepsRest))
                }
                if (health.historyAvailable && !health.historyGranted && (period == StatsPeriod.Y1 || period == StatsPeriod.ALL)) {
                    Spacer(Modifier.height(8.dp))
                    Text("Health Connect dà solo i passi dei 30 giorni prima del collegamento, salvo permesso per la cronologia.", style = T.caption, color = c.ink3)
                    TextLink("Consenti i passi più vecchi", onConnect)
                }
            }
        }
    }
}

@Composable
private fun StepsBars(days: List<Long>, steps: Map<Long, Int>, goal: Int, modifier: Modifier) {
    val c = M.c
    val measurer = rememberTextMeasurer()
    val avg = remember(days, steps) { Stats.rollingSteps(days, steps) }
    val grow = remember(days.size) { Animatable(0f) }
    val motion = M.motion
    LaunchedEffect(days.size) { if (motion) grow.animateTo(1f, tween(800, 150, Ease.Standard)) else grow.snapTo(1f) }
    val top = ((days.mapNotNull { steps[it] } + goal).maxOrNull() ?: goal).toFloat() * 1.05f
    val labelStyle = T.caption.copy(color = c.ink3, fontSize = T.caption.fontSize * 0.92f)
    Canvas(modifier) {
        val bottomH = 18.dp.toPx()
        val labelW = 30.dp.toPx()
        val w = size.width - labelW
        val h = size.height - bottomH
        val slot = w / days.size
        val bw = (slot * 0.7f).coerceAtLeast(1f)
        fun y(v: Float) = h - v / top * h
        days.forEachIndexed { i, d ->
            val v = steps[d] ?: return@forEachIndexed
            if (v <= 0) return@forEachIndexed
            val vh = v * grow.value
            drawRoundRect(if (v >= goal) c.accent else c.accentSoft, Offset(slot * i + (slot - bw) / 2, y(vh)), Size(bw, h - y(vh)), CornerRadius(minOf(bw / 2, 3.dp.toPx())))
        }
        // Obiettivo
        val gy = y(goal.toFloat())
        drawLine(c.ink3, Offset(0f, gy), Offset(w, gy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
        val gt = measurer.measure(formatSteps(goal), labelStyle)
        drawText(gt, topLeft = Offset(w + 6.dp.toPx(), gy - gt.size.height / 2))
        val mt = measurer.measure(formatSteps((top / 1.05f).toInt()), labelStyle)
        if (gy - mt.size.height > mt.size.height) drawText(mt, topLeft = Offset(w + 6.dp.toPx(), 0f))
        // Media mobile
        val p = Path(); var started = false
        avg.forEachIndexed { i, v ->
            if (v == null) { started = false; return@forEachIndexed }
            val x = slot * i + slot / 2; val yy = y(v * grow.value)
            if (!started) { p.moveTo(x, yy); started = true } else p.lineTo(x, yy)
        }
        drawPath(p, c.ink, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        listOf(0, days.size / 2, days.size - 1).distinct().forEach { i ->
            val txt = measurer.measure(Fmt.dateShort(LocalDate.ofEpochDay(days[i])), labelStyle)
            val cx = slot * i + slot / 2
            drawText(txt, topLeft = Offset((cx - txt.size.width / 2f).coerceIn(0f, w - txt.size.width), h + 4.dp.toPx()))
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.body14, color = M.c.ink2, modifier = Modifier.weight(1f))
        Text(value, style = T.body14.copy(fontWeight = FontWeight.SemiBold), color = M.c.ink)
    }
}

// --- Abitudini ---

@Composable
private fun HabitsCard(s: StatsSummary, modifier: Modifier) {
    val c = M.c
    Card(modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
        CardHeader("Abitudini", null)
        Spacer(Modifier.height(14.dp))
        val max = s.weekday.maxOrNull()?.coerceAtLeast(1) ?: 1
        val grow = remember(s.weekday) { Animatable(0f) }
        val motion = M.motion
        LaunchedEffect(s.weekday) { if (motion) grow.animateTo(1f, tween(700, 150, Ease.Standard)) else grow.snapTo(1f) }
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            java.time.DayOfWeek.entries.forEachIndexed { i, d ->
                val n = s.weekday[i]
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text(if (n > 0) "$n" else "", style = ts(11, FontWeight.SemiBold), color = c.ink2)
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight((n.toFloat() / max * grow.value).coerceAtLeast(0.03f))
                                .clip(RoundedCornerShape(6.dp)).background(if (n == max && n > 0) c.accent else c.accentSoft)
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(Fmt.dayLetter(d), style = T.captionBold, color = c.ink3)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val best = s.weekday.withIndex().filter { it.value > 0 }.maxByOrNull { it.value }
        Line("Giorno preferito", best?.let { Fmt.dayName(java.time.DayOfWeek.of(it.index + 1)) } ?: "—")
        Line("Di solito inizi alle", s.typicalStartMin?.let { Fmt.time(it) } ?: "—")
        Line("Fatica media", s.fatigueAvg?.let { "${Fmt.kg(it)}/10" } ?: "—")
        Line("Ripartenze dopo un salto", if (s.skipped > 0) "${s.recovered} su ${s.skipped}" else "nessun salto")
    }
}

@Composable
private fun TopVideosCard(top: List<Pair<Long, Int>>, videos: List<VideoEntity>, modifier: Modifier) {
    val c = M.c
    Card(modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp)) {
        CardHeader("Video più fatti", null)
        Spacer(Modifier.height(10.dp))
        val max = top.first().second
        top.forEachIndexed { k, (id, n) ->
            val title = videos.firstOrNull { it.id == id }?.title ?: "Video eliminato"
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${k + 1}", style = T.captionBold, color = c.ink3, modifier = Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = T.body14, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth(n.toFloat() / max).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (k == 0) c.accent else c.accentSoft))
                }
                Spacer(Modifier.width(12.dp))
                Text("${n}×", style = T.body14.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
            }
        }
    }
}
