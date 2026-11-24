package it.mithril.app.ui.weight

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mithril.app.data.WeightEntity
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Goal
import it.mithril.app.domain.GoalStatus
import it.mithril.app.domain.Milestones
import it.mithril.app.domain.Nutrition
import it.mithril.app.domain.Trend
import it.mithril.app.domain.Week
import it.mithril.app.domain.toDomain
import it.mithril.app.health.Health
import it.mithril.app.ui.BottomGap
import it.mithril.app.ui.Dest
import it.mithril.app.ui.LocalNav
import it.mithril.app.ui.SheetKind
import it.mithril.app.ui.components.Card
import it.mithril.app.ui.components.Divider
import it.mithril.app.ui.components.GrowBar
import it.mithril.app.ui.components.LocalToast
import it.mithril.app.ui.components.Overline
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.components.RoundIconButton
import it.mithril.app.ui.components.SmallChip
import it.mithril.app.ui.components.StatValue
import it.mithril.app.ui.components.Symbol
import it.mithril.app.ui.components.TextLink
import it.mithril.app.ui.components.TonalButton
import it.mithril.app.ui.rememberRepo
import it.mithril.app.ui.statusBarTop
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T
import it.mithril.app.ui.theme.rise
import it.mithril.app.ui.theme.ts
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun WeightScreen() {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val today by repo.today.collectAsStateWithLifecycle()
    val weightsOrNull by repo.weights.collectAsStateWithLifecycle()
    val slots by repo.slots.collectAsStateWithLifecycle()
    val logs by repo.logs.collectAsStateWithLifecycle()
    val plans by repo.weekPlans.collectAsStateWithLifecycle()
    val nutrition by repo.nutrition.collectAsStateWithLifecycle()
    val settings by repo.appSettings.collectAsStateWithLifecycle()
    val health by Health.state.collectAsStateWithLifecycle()
    val weights = weightsOrNull ?: emptyList()
    val pairs = remember(weights) { weights.map { it.date to it.kg } }
    val trend = remember(pairs, today) { Trend.of(pairs, today.toEpochDay()) }
    var range by rememberSaveable { mutableStateOf(Range.M3) }
    val enough = weights.size >= Trend.MIN_WEIGH_INS
    val epoch = nav.tabEpoch

    val goals = remember(plans) { plans.associate { it.weekStart to it.goalSessions } }
    val recentActiveWeeks = remember(today, slots, logs, goals) {
        val lastClosed = Week.start(today).minusWeeks(1)
        val w1 = Week.summary(lastClosed, today, slots, logs, Week.goalFor(lastClosed, goals)).active
        val w2 = Week.summary(lastClosed.minusWeeks(1), today, slots, logs, Week.goalFor(lastClosed.minusWeeks(1), goals)).active
        if (w1 && w2) 2 else if (w1) 1 else 0
    }
    val calSummary = remember(nutrition, today, settings.caloriesGoal) {
        Nutrition.summarize(nutrition.map { it.toDomain() }, today.minusDays(7), today, settings.caloriesGoal)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = statusBarTop() + 14.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Peso", style = T.screenTitle, color = M.c.ink, modifier = Modifier.weight(1f))
            RoundIconButton("settings", { nav.push(Dest.Settings) }, size = 40.dp, iconSize = 22.dp, bg = M.c.bg, fg = M.c.ink2, contentDescription = "Impostazioni")
            Spacer(Modifier.width(6.dp))
            TonalButton("Pesata", { nav.sheet = SheetKind.Weigh() }, icon = "add", height = 40.dp)
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                weightsOrNull == null -> {}
                weights.isEmpty() -> Card(Modifier.rise(0, epoch).fillMaxWidth()) {
                    Text("Qui conta la tendenza", style = T.heroTitle, color = M.c.ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Il peso di un singolo giorno oscilla per acqua, sale e sonno. Mithril calcola una media mobile delle pesate: " +
                            "la linea del trend si muove piano e mostra dove stai andando davvero. Pesati quando vuoi, meglio al mattino.",
                        style = T.body, color = M.c.ink2,
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Fai la prima pesata", { nav.sheet = SheetKind.Weigh() }, Modifier.fillMaxWidth(), icon = "add", height = 48.dp)
                }
                !enough -> Card(Modifier.rise(0, epoch).fillMaxWidth()) {
                    Text("Ultima pesata", style = T.secondary, color = M.c.ink2)
                    StatValue(Fmt.kg(weights.last().kg), "kg")
                    Spacer(Modifier.height(8.dp))
                    val n = Trend.MIN_WEIGH_INS - weights.size
                    Text("Ancora $n ${if (n == 1) "pesata" else "pesate"} e vedrai il trend. Nessuna fretta.", style = T.body, color = M.c.ink2)
                }
                else -> TrendCard(trend, today, range, settings.goalKg, Modifier.rise(0, epoch)) { range = it }
            }
            if (enough) {
                GoalCard(trend, today, settings.goalKg, settings.weeklyRateKg, settings.goalStartKg, recentActiveWeeks, Modifier.rise(1, epoch))
            } else if (weights.isNotEmpty() && settings.goalKg == null) {
                GoalCard(trend, today, null, settings.weeklyRateKg, null, recentActiveWeeks, Modifier.rise(1, epoch))
            }
            CaloriesLinkCard(health.nutritionGranted, calSummary.avgKcal, settings.caloriesGoal, Modifier.rise(2, epoch)) { nav.push(Dest.Calories) }
            if (weights.isNotEmpty()) WeighInsCard(weights.reversed().take(10), weights.size, Modifier.rise(3, epoch))
            Spacer(Modifier.height(BottomGap))
        }
    }
}

@Composable
private fun CaloriesLinkCard(granted: Boolean, weekAvgKcal: Int?, goalKcal: Int, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.fillMaxWidth(), radius = 22.dp, padding = PaddingValues(16.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Symbol("local_fire_department", 22.dp, M.c.accent, filled = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Calorie (MyFitnessPal)", style = T.title15, color = M.c.ink)
                Text(
                    when {
                        weekAvgKcal != null -> "Media ultimi 7 gg: ${Fmt.thousands(weekAvgKcal)} / ${Fmt.thousands(goalKcal)} kcal"
                        granted -> "Nessuna giornata tracciata negli ultimi 7 giorni"
                        else -> "Collega Health Connect per vedere bilancio e fabbisogno"
                    },
                    style = T.secondary, color = M.c.ink2,
                )
            }
            Symbol("chevron_right", 22.dp, M.c.ink2)
        }
    }
}

@Composable
private fun TrendCard(trend: Trend, today: LocalDate, range: Range, goalKg: Double?, modifier: Modifier, onRange: (Range) -> Unit) {
    val t = today.toEpochDay()
    val now = trend.at(t)
    val rate = trend.weeklyRate(t)
    Card(modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Trend attuale", style = T.secondary, color = M.c.ink2)
                if (now != null) StatValue(Fmt.kg(now), "kg")
            }
            // Un solo ritmo per tutta la schermata: lo stesso di proiezione e obiettivo.
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 6.dp)) {
                if (rate != null) {
                    Text("${Fmt.signedRate(rate)} kg/sett.", style = ts(17, FontWeight.SemiBold), color = M.c.ink)
                    Text(if (trend.slopeSpan(t) == 14) "ritmo · ultime 2 settimane" else "ritmo · ultima settimana", style = T.caption, color = M.c.ink3)
                } else Text("Il ritmo compare\ndopo una settimana", style = T.caption, color = M.c.ink3, textAlign = TextAlign.End)
            }
        }
        Spacer(Modifier.height(14.dp))
        Segmented(range, onRange)
        Spacer(Modifier.height(4.dp))
        WeightChart(trend, today, range, goalKg, Modifier.fillMaxWidth().height(210.dp))
        Spacer(Modifier.height(12.dp))
        Legend()
        Spacer(Modifier.height(10.dp))
        Text(projectionNote(trend, today, range, goalKg), style = T.secondary, color = M.c.ink2)
    }
}

/** La proiezione a parole: fino a quando arriva e quale peso prevede, al ritmo mostrato in alto. */
private fun projectionNote(trend: Trend, today: LocalDate, range: Range, goalKg: Double?): String {
    val p = trend.projection(today.toEpochDay(), range.days, goalKg) ?: return "La proiezione compare dopo una settimana di trend."
    val day = Fmt.dateShort(today.plusDays(p.days.toLong()), today)
    return when {
        p.stable -> "Trend stabile: se resta così, il $day sarai ancora a ${Fmt.kg(p.endKg)} kg."
        p.reachesGoal -> "A questo ritmo arrivi all'obiettivo di ${Fmt.kg(p.endKg)} kg verso il $day."
        else -> "A questo ritmo, il $day saresti a ${Fmt.kg(p.endKg)} kg."
    }
}

@Composable
private fun Segmented(range: Range, onRange: (Range) -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp)).background(M.c.surface2).padding(3.dp)) {
        Range.entries.forEach { r ->
            val sel = r == range
            val bg by animateColorAsState(if (sel) M.c.surface else M.c.surface2, tween(250), label = "seg")
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .then(if (sel && !M.c.dark) Modifier.shadow(2.dp, RoundedCornerShape(17.dp), ambientColor = Color(0x223C281E), spotColor = Color(0x223C281E)) else Modifier)
                    .clip(RoundedCornerShape(17.dp)).background(bg).clickable { onRange(r) },
                contentAlignment = Alignment.Center,
            ) { Text(r.label, style = ts(13, if (sel) FontWeight.SemiBold else FontWeight.Medium), color = if (sel) M.c.ink else M.c.ink2) }
        }
    }
}

@Composable
private fun Legend() {
    val c = M.c
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(14.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.accent))
            Spacer(Modifier.width(5.dp)); Text("trend", style = T.caption, color = M.c.ink2)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.ink3.copy(alpha = 0.6f)))
            Spacer(Modifier.width(5.dp)); Text("pesate", style = T.caption, color = M.c.ink2)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.width(16.dp).height(3.dp)) {
                drawLine(c.accent.copy(alpha = 0.7f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            }
            Spacer(Modifier.width(5.dp)); Text("proiezione", style = T.caption, color = M.c.ink2)
        }
    }
}

@Composable
private fun GoalCard(
    trend: Trend,
    today: LocalDate,
    goalKg: Double?,
    rate: Double,
    startKg: Double?,
    recentActiveWeeks: Int,
    modifier: Modifier,
) {
    val nav = LocalNav.current
    Card(modifier.fillMaxWidth(), radius = 22.dp, padding = PaddingValues(16.dp), onClick = { nav.push(Dest.Settings) }) {
        if (goalKg == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Obiettivo", style = T.secondary, color = M.c.ink2)
                    Text("Imposta un obiettivo", style = ts(18, FontWeight.SemiBold), color = M.c.ink)
                }
                Symbol("chevron_right", 22.dp, M.c.ink2)
            }
            return@Card
        }
        val status = Goal.status(trend, today, goalKg, rate, startKg)
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Obiettivo", style = T.secondary, color = M.c.ink2)
                Text("${Fmt.kg(goalKg)} kg", style = ts(22, FontWeight.SemiBold), color = M.c.ink)
            }
            Column(horizontalAlignment = Alignment.End) {
                when {
                    status == null -> {}
                    status.remainingKg <= 0.0 -> Text("Raggiunto ✓", style = T.title15, color = M.c.accent)
                    status.eta != null -> {
                        Text("Con questo ritmo", style = T.secondary, color = M.c.ink2)
                        Text(Fmt.eta(status.eta, today), style = T.title15, color = M.c.ink)
                    }
                }
            }
        }
        if (status == null) return@Card
        Spacer(Modifier.height(14.dp))
        GrowBar(status.progress, Modifier.fillMaxWidth(), height = 8.dp)
        Spacer(Modifier.height(6.dp))
        Row {
            Text("${Fmt.kg(status.doneKg)} kg fatti", style = T.caption, color = M.c.ink2, modifier = Modifier.weight(1f))
            Text("${Fmt.kg(status.remainingKg)} kg da fare", style = T.caption, color = M.c.ink2)
        }
        val backpack = Milestones.backpackEquivalent(status.doneKg)
        if (backpack != null && status.direction < 0) {
            Spacer(Modifier.height(4.dp))
            Text("È come $backpack che non porti più sulle scale.", style = T.caption, color = M.c.ink3)
        }
        val milestone = if (status.remainingKg > 0.0) Milestones.forTrend(trend, today, goalKg, startKg) else null
        if (milestone != null) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(M.c.surface2).padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (milestone.justCrossedKg != null) {
                    SmallChip("Sfondati i ${Fmt.kg(milestone.justCrossedKg)} kg ✓")
                    Spacer(Modifier.height(6.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (status.direction < 0) "Prossima tappa: sotto ${Fmt.kg(milestone.nextKg)} kg"
                        else "Prossima tappa: sopra ${Fmt.kg(milestone.nextKg)} kg",
                        style = T.captionBold, color = M.c.ink, modifier = Modifier.weight(1f),
                    )
                    Text(
                        "−${Fmt.kg(milestone.remainingKg)} kg" + (milestone.etaDays?.let { " · ~$it gg" } ?: ""),
                        style = T.caption, color = M.c.ink2,
                    )
                }
                Spacer(Modifier.height(6.dp))
                GrowBar(milestone.progress, Modifier.fillMaxWidth(), height = 5.dp, track = M.c.surface, delayMs = 200)
            }
        }
        Spacer(Modifier.height(12.dp))
        Divider(Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (status.onTrack) M.c.accent else M.c.ink3))
            Spacer(Modifier.width(10.dp))
            Text(planLine(status, trend.weeklyRate(today.toEpochDay()), rate * status.direction, recentActiveWeeks), style = T.body14, color = M.c.ink, modifier = Modifier.weight(1f))
        }
    }
}

/** Il ritmo attuale (lo stesso del grafico) confrontato col ritmo previsto in Impostazioni. */
private fun planLine(s: GoalStatus, current: Double?, plan: Double, recentActiveWeeks: Int): String {
    val planTxt = Fmt.signedRate(plan)
    val cur = current?.let { Fmt.signedRate(it) }
    return when {
        s.remainingKg <= 0.0 -> "Ora conta mantenere il trend."
        cur == null -> "Il ritmo compare dopo una settimana di trend. Piano: $planTxt kg a settimana."
        s.onTrack -> "In linea col piano: $cur kg a settimana (previsti $planTxt)."
        s.weeklyTowards != null && s.weeklyTowards < -Trend.STABLE_SLOPE * 7 -> "Il trend si allontana dall'obiettivo: $cur kg a settimana (previsti $planTxt)."
        s.stable && recentActiveWeeks >= 2 -> "Trend fermo, ma $recentActiveWeeks settimane attive di fila: i plateau sono normali mentre ti alleni."
        s.stable -> "Trend fermo: il piano prevede $planTxt kg a settimana."
        else -> "Più lento del piano: $cur kg a settimana (previsti $planTxt)."
    }
}

@Composable
private fun WeighInsCard(list: List<WeightEntity>, total: Int, modifier: Modifier) {
    val nav = LocalNav.current
    Card(modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
        Overline("Pesate")
        Spacer(Modifier.height(8.dp))
        WeighInRows(list)
        if (total > list.size) Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            TextLink("Mostra tutte ($total)", { nav.push(Dest.AllWeights) })
        }
    }
}

@Composable
fun ColumnScope.WeighInRows(list: List<WeightEntity>) {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val toast = LocalToast.current
    list.forEachIndexed { i, w ->
        if (i > 0) Divider(Modifier.fillMaxWidth().padding(end = 8.dp))
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.dayShort(LocalDate.ofEpochDay(w.date)), style = T.body14, color = M.c.ink2, modifier = Modifier.weight(1f))
            Text(Fmt.kg(w.kg), style = ts(16, FontWeight.SemiBold), color = M.c.ink)
            Spacer(Modifier.width(8.dp))
            RoundIconButton("edit", { nav.sheet = SheetKind.Weigh(LocalDate.ofEpochDay(w.date)) }, size = 40.dp, iconSize = 20.dp, bg = M.c.surface, fg = M.c.ink2, contentDescription = "Modifica")
            RoundIconButton("delete", {
                repo.scope.launch { repo.deleteWeight(w.date) }
                toast.show("Pesata eliminata", "Annulla") { repo.scope.launch { repo.restoreWeight(w) } }
            }, size = 40.dp, iconSize = 20.dp, bg = M.c.surface, fg = M.c.ink2, contentDescription = "Elimina")
        }
    }
}

@Composable
fun AllWeightsScreen() {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val weights by repo.weights.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(M.c.bg).verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars).padding(top = statusBarTop() + 6.dp, start = 12.dp, end = 16.dp, bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton("arrow_back", { nav.pop() }, size = 44.dp, bg = M.c.bg, contentDescription = "Indietro")
            Text("Tutte le pesate", style = T.sheetTitle, color = M.c.ink)
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth().padding(start = 4.dp), padding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            WeighInRows((weights ?: emptyList()).reversed())
        }
    }
}
