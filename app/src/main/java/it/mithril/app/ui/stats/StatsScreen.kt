package it.mithril.app.ui.stats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mithril.app.domain.Correlations
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Stats
import it.mithril.app.domain.StatsPeriod
import it.mithril.app.domain.StatsSummary
import it.mithril.app.domain.Trend
import it.mithril.app.domain.toDomain
import it.mithril.app.health.Health
import it.mithril.app.health.StepsState
import it.mithril.app.ui.BottomGap
import it.mithril.app.ui.Dest
import it.mithril.app.ui.LocalNav
import it.mithril.app.ui.Tab
import it.mithril.app.ui.components.Card
import it.mithril.app.ui.components.Symbol
import it.mithril.app.ui.components.TextLink
import it.mithril.app.ui.rememberRepo
import it.mithril.app.ui.statusBarTop
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T
import it.mithril.app.ui.theme.rise
import it.mithril.app.ui.theme.ts
import it.mithril.app.ui.weight.Range
import it.mithril.app.ui.weight.WeightChart
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Pannello Statistiche: numeri del periodo, calendario con sessioni, passi, peso e calorie, heatmap e correlazioni. */
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
    val nutritionEntities by repo.nutrition.collectAsStateWithLifecycle()
    val settings by repo.appSettings.collectAsStateWithLifecycle()
    val health by Health.state.collectAsStateWithLifecycle()
    val epoch = nav.tabEpoch

    var period by rememberSaveable { mutableStateOf(StatsPeriod.W4) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today)) }
    var selectedDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var calMode by rememberSaveable { mutableStateOf(CalMode.ALL) }
    var weekMinutes by rememberSaveable { mutableStateOf(false) }

    val goals = remember(plans) { plans.associate { it.weekStart to it.goalSessions } }
    val weights = remember(weightsOrNull) { weightsOrNull?.map { it.date to it.kg } ?: emptyList() }
    val nutrition = remember(nutritionEntities) { nutritionEntities.map { it.toDomain() } }
    val nutritionByDay = remember(nutrition) { nutrition.associateBy { it.date } }
    val trend = remember(weights, today) { Trend.of(weights, today.toEpochDay()) }
    val from = remember(period, today, logs, weights, slots, nutrition) { Stats.from(period, today, logs, weights, slots, nutrition) }

    var history by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var loadedFrom by remember { mutableStateOf<LocalDate?>(null) }
    var loadedNutrFrom by remember { mutableStateOf<LocalDate?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val need = minOf(from, month.atDay(1))
    LaunchedEffect(health.granted, health.nutritionGranted, health.historyGranted, need, reload) {
        if (health.granted) {
            val lf = loadedFrom
            if (lf == null || need.isBefore(lf)) {
                history = history + Health.stepsBetween(context, need, lf?.minusDays(1) ?: today)
                loadedFrom = need
            }
        }
        if (health.nutritionGranted) {
            val nf = loadedNutrFrom
            if (nf == null || need.isBefore(nf)) {
                Health.syncNutrition(context, need, nf?.minusDays(1) ?: today)
                loadedNutrFrom = need
            }
        }
    }
    val steps = remember(history, health.byDay) { history + health.byDay }
    val healthLauncher = rememberLauncherForActivityResult(Health.permissionContract()) {
        scope.launch { Health.refresh(context); loadedFrom = null; loadedNutrFrom = null; history = emptyMap(); reload++ }
    }

    val s = remember(from, today, slots, logs, goals, weights, steps, settings.stepsGoal, nutrition, settings.caloriesGoal) {
        Stats.summary(from, today, slots, logs, goals, weights, steps, settings.stepsGoal, nutrition = nutrition, caloriesGoal = settings.caloriesGoal)
    }
    val corr = remember(from, today, slots, logs, goals, trend, nutrition, settings.caloriesGoal) {
        Correlations.analyze(from, today, slots, logs, goals, trend, nutrition, settings.caloriesGoal)
    }
    val all = videos ?: emptyList()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = statusBarTop() + 14.dp)) {
        Text("Statistiche", style = T.screenTitle, color = M.c.ink, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(14.dp))
        Segmented(StatsPeriod.entries, period, { it.label }, { period = it })
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Kpis(s, period, health, settings.caloriesGoal, { nav.push(Dest.Calories) }, Modifier.rise(0, epoch))
            CalendarCard(
                month, { month = it }, today, selectedDay, { selectedDay = it }, calMode, { calMode = it },
                slots, logs, weights, trend, steps, settings.stepsGoal, nutritionByDay, settings.caloriesGoal, all, Modifier.rise(1, epoch),
            )
            WeeksCard(s, weekMinutes, { weekMinutes = it }, Modifier.rise(2, epoch))
            AnnualHeatmapCard(today, logs, s.yearSessions, s.yearMinutes, Modifier.rise(3, epoch))
            CorrelationCard(corr, settings.caloriesGoal, Modifier.rise(4, epoch))
            StepsCard(s, steps, settings.stepsGoal, health, period, { healthLauncher.launch(Health.requestSet) }, Modifier.rise(5, epoch))
            if (weights.size >= Trend.MIN_WEIGH_INS) {
                Card(Modifier.rise(6, epoch).fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
                    CardHeader("Peso", s.trendChange?.let { "trend ${Fmt.signedKg(it)} kg nel periodo" })
                    Spacer(Modifier.height(4.dp))
                    val range = when (period) { StatsPeriod.W4 -> Range.M1; StatsPeriod.M3 -> Range.M3; else -> Range.Y1 }
                    WeightChart(trend, today, range, settings.goalKg, Modifier.fillMaxWidth().height(190.dp), forecast = false)
                    Spacer(Modifier.height(10.dp))
                    TextLink("Dettagli nella scheda Peso", { nav.select(Tab.WEIGHT) })
                }
            }
            HabitsCard(s, Modifier.rise(7, epoch))
            if (s.topVideos.isNotEmpty()) TopVideosCard(s.topVideos, all, Modifier.rise(8, epoch))
            Spacer(Modifier.height(BottomGap))
        }
    }
}

@Composable
private fun Kpis(
    s: StatsSummary,
    period: StatsPeriod,
    health: StepsState,
    caloriesGoal: Int,
    onCalories: () -> Unit,
    modifier: Modifier,
) {
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
                    !health.available -> "Health Connect non disponibile"
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
        if (health.available || s.caloriesDays > 0) {
            KpiRow(
                Kpi(
                    "Calorie · media",
                    s.caloriesAvg?.let { Fmt.thousands(it) } ?: "—",
                    when {
                        s.caloriesAvg != null -> "target ${Fmt.thousands(caloriesGoal)} kcal"
                        !health.nutritionGranted -> "tocca per collegare"
                        else -> "nessun dato nel periodo"
                    },
                    onClick = onCalories,
                ),
                Kpi(
                    "Giorni in target",
                    if (s.caloriesDays > 0) "${s.caloriesGoalDays}/${s.caloriesDays}" else "—",
                    if (s.caloriesDays > 0) "su giorni tracciati" else "MyFitnessPal",
                    onClick = onCalories,
                ),
            )
        }
    }
}

private data class Kpi(
    val label: String,
    val value: String,
    val sub: String,
    val icon: String? = null,
    val onClick: (() -> Unit)? = null,
)

@Composable
private fun KpiRow(a: Kpi, b: Kpi) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(a, b).forEach { k ->
            Card(Modifier.weight(1f).fillMaxHeight(), radius = 22.dp, padding = PaddingValues(16.dp), onClick = k.onClick) {
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
