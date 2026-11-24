package it.mithril.app.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.mithril.app.data.SessionLogEntity
import it.mithril.app.data.VideoEntity
import it.mithril.app.domain.CorrelationSummary
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Stats
import it.mithril.app.domain.StatsPeriod
import it.mithril.app.domain.StatsSummary
import it.mithril.app.domain.Week
import it.mithril.app.domain.WeekBar
import it.mithril.app.health.StepsState
import it.mithril.app.ui.components.Card
import it.mithril.app.ui.components.Divider
import it.mithril.app.ui.components.TextLink
import it.mithril.app.ui.components.TonalButton
import it.mithril.app.ui.theme.Ease
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T
import it.mithril.app.ui.theme.ts
import it.mithril.app.widget.formatSteps
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
internal fun CardHeader(title: String, sub: String?) {
    Text(title, style = T.cardTitle, color = M.c.ink)
    if (sub != null) Text(sub, style = T.secondary, color = M.c.ink2)
}

@Composable
internal fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.body14, color = M.c.ink2, modifier = Modifier.weight(1f))
        Text(value, style = T.body14.copy(fontWeight = FontWeight.SemiBold), color = M.c.ink)
    }
}

// --- Settimane ---

@Composable
internal fun WeeksCard(s: StatsSummary, minutes: Boolean, onMinutes: (Boolean) -> Unit, modifier: Modifier) {
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
            drawText(txt, topLeft = Offset((cx - txt.size.width / 2f).coerceIn(0f, (size.width - txt.size.width).coerceAtLeast(0f)), size.height - bottomH + 4.dp.toPx()))
        }
    }
}

// --- Heatmap annuale (365 giorni) ---

@Composable
internal fun AnnualHeatmapCard(
    today: LocalDate,
    logs: List<SessionLogEntity>,
    yearSessions: Int,
    yearMinutes: Int,
    modifier: Modifier,
) {
    val c = M.c
    val dayType = remember(logs) {
        val map = HashMap<Long, Int>() // 2 = full, 1 = mini
        logs.forEach { l ->
            val prev = map[l.date] ?: 0
            val cur = if (l.isMini) 1 else 2
            if (cur > prev) map[l.date] = cur
        }
        map
    }
    val lastWeekStart = Week.start(today)
    val firstWeekStart = lastWeekStart.minusWeeks(51)
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) { scroll.scrollTo(scroll.maxValue) }

    val hours = yearMinutes / 60
    val sub = when {
        yearSessions == 0 -> "Ogni quadratino è un giorno di allenamento."
        hours >= 1 -> "$yearSessions ${if (yearSessions == 1) "allenamento" else "allenamenti"} negli ultimi 12 mesi · $hours h su di te"
        else -> "$yearSessions ${if (yearSessions == 1) "allenamento" else "allenamenti"} negli ultimi 12 mesi · $yearMinutes min su di te"
    }
    Card(modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
        CardHeader("Costanza nell'anno", sub)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (w in 0..51) {
                val ws = firstWeekStart.plusWeeks(w.toLong())
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (d in 0..6) {
                        val date = ws.plusDays(d.toLong())
                        val future = date.isAfter(today)
                        val kind = dayType[date.toEpochDay()] ?: 0
                        val col = when {
                            future -> Color.Transparent
                            kind == 2 -> c.accent
                            kind == 1 -> c.accentSoft
                            else -> c.surface2
                        }
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.5.dp)).background(col))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.5.dp)).background(c.accent)); Spacer(Modifier.width(5.dp)); Text("sessione", style = T.caption, color = c.ink2) }
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.5.dp)).background(c.accentSoft)); Spacer(Modifier.width(5.dp)); Text("mini", style = T.caption, color = c.ink2) }
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.5.dp)).background(c.surface2)); Spacer(Modifier.width(5.dp)); Text("riposo", style = T.caption, color = c.ink2) }
        }
    }
}

// --- Correlazione settimane, calorie e peso ---

@Composable
internal fun CorrelationCard(corr: CorrelationSummary, caloriesGoal: Int, modifier: Modifier) {
    val hasWeightActive = corr.activeWeightAvg != null && corr.inactiveWeightAvg != null
    val hasWeightKcal = corr.onTargetKcalWeightAvg != null && corr.overTargetKcalWeightAvg != null
    val hasDayKcal = corr.trainingDayKcalAvg != null && corr.restDayKcalAvg != null
    val recentWeeks = corr.weeks.filter { it.weightDeltaKg != null || it.avgKcal != null || it.sessions > 0 }.takeLast(6).reversed()
    if (!hasWeightActive && !hasWeightKcal && !hasDayKcal && recentWeeks.isEmpty()) return

    Card(modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
        CardHeader("Allenamenti, calorie e peso", "Sulle settimane concluse del periodo")
        Spacer(Modifier.height(12.dp))
        if (hasWeightActive) {
            Line("Settimane attive (${corr.activeWeeksCount})", "${Fmt.signedRate(corr.activeWeightAvg!!)} kg/sett.")
            Line("Altre settimane (${corr.inactiveWeeksCount})", "${Fmt.signedRate(corr.inactiveWeightAvg!!)} kg/sett.")
        } else if (corr.activeWeightAvg != null) {
            Line("Settimane attive (${corr.activeWeeksCount})", "${Fmt.signedRate(corr.activeWeightAvg)} kg/sett.")
        }
        if (hasWeightKcal) {
            if (hasWeightActive || corr.activeWeightAvg != null) {
                Spacer(Modifier.height(6.dp)); Divider(Modifier.fillMaxWidth()); Spacer(Modifier.height(6.dp))
            }
            Line("Calorie ≤ ${Fmt.thousands(caloriesGoal)} (${corr.onTargetKcalWeeks} sett.)", "${Fmt.signedRate(corr.onTargetKcalWeightAvg!!)} kg/sett.")
            Line("Calorie sopra (${corr.overTargetKcalWeeks} sett.)", "${Fmt.signedRate(corr.overTargetKcalWeightAvg!!)} kg/sett.")
        }
        if (hasDayKcal) {
            if (hasWeightActive || hasWeightKcal) {
                Spacer(Modifier.height(6.dp)); Divider(Modifier.fillMaxWidth()); Spacer(Modifier.height(6.dp))
            }
            Line("Calorie nei giorni di allenamento", "${Fmt.thousands(corr.trainingDayKcalAvg!!)} kcal")
            Line("Calorie nei giorni di riposo", "${Fmt.thousands(corr.restDayKcalAvg!!)} kcal")
        }
        if (recentWeeks.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Divider(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("Settimana", style = T.caption, color = M.c.ink3, modifier = Modifier.weight(1.2f))
                Text("Sess.", style = T.caption, color = M.c.ink3, modifier = Modifier.weight(0.7f))
                Text("Kcal/g", style = T.caption, color = M.c.ink3, modifier = Modifier.weight(0.9f))
                Text("Δ peso", style = T.caption, color = M.c.ink3)
            }
            Spacer(Modifier.height(4.dp))
            recentWeeks.forEach { w ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.dateShort(w.start), style = T.captionBold, color = M.c.ink, modifier = Modifier.weight(1.2f))
                    Text(
                        "${w.sessions}/${w.goal}" + if (w.active) " ✓" else "",
                        style = T.caption, color = if (w.active) M.c.accent else M.c.ink2, modifier = Modifier.weight(0.7f),
                    )
                    Text(w.avgKcal?.let { Fmt.thousands(it) } ?: "—", style = T.caption, color = M.c.ink2, modifier = Modifier.weight(0.9f))
                    Text(
                        w.weightDeltaKg?.let { "${Fmt.signedKg(it)} kg" } ?: "—",
                        style = T.captionBold, color = M.c.ink,
                    )
                }
            }
        }
    }
}

// --- Passi ---

@Composable
internal fun StepsCard(s: StatsSummary, steps: Map<Long, Int>, goal: Int, health: StepsState, period: StatsPeriod, onConnect: () -> Unit, modifier: Modifier) {
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
        val w = (size.width - labelW).coerceAtLeast(1f)
        val h = (size.height - bottomH).coerceAtLeast(1f)
        val slot = w / days.size
        val bw = (slot * 0.7f).coerceAtLeast(1f)
        fun y(v: Float) = h - v / top * h
        days.forEachIndexed { i, d ->
            val v = steps[d] ?: return@forEachIndexed
            if (v <= 0) return@forEachIndexed
            val vh = v * grow.value
            drawRoundRect(if (v >= goal) c.accent else c.accentSoft, Offset(slot * i + (slot - bw) / 2, y(vh)), Size(bw, h - y(vh)), CornerRadius(minOf(bw / 2, 3.dp.toPx())))
        }
        val gy = y(goal.toFloat())
        drawLine(c.ink3, Offset(0f, gy), Offset(w, gy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
        val gt = measurer.measure(formatSteps(goal), labelStyle)
        drawText(gt, topLeft = Offset(w + 6.dp.toPx(), (gy - gt.size.height / 2).coerceIn(0f, (h - gt.size.height).coerceAtLeast(0f))))
        val mt = measurer.measure(formatSteps((top / 1.05f).toInt()), labelStyle)
        if (gy - mt.size.height > mt.size.height) drawText(mt, topLeft = Offset(w + 6.dp.toPx(), 0f))
        val p = Path(); var started = false
        avg.forEachIndexed { i, v ->
            if (v == null) { started = false; return@forEachIndexed }
            val x = slot * i + slot / 2; val yy = y(v * grow.value)
            if (!started) { p.moveTo(x, yy); started = true } else p.lineTo(x, yy)
        }
        drawPath(p, c.ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        listOf(0, days.size / 2, days.size - 1).distinct().forEach { i ->
            val txt = measurer.measure(Fmt.dateShort(LocalDate.ofEpochDay(days[i])), labelStyle)
            val cx = slot * i + slot / 2
            drawText(txt, topLeft = Offset((cx - txt.size.width / 2f).coerceIn(0f, (w - txt.size.width).coerceAtLeast(0f)), h + 4.dp.toPx()))
        }
    }
}

// --- Abitudini ---

@Composable
internal fun HabitsCard(s: StatsSummary, modifier: Modifier) {
    val c = M.c
    Card(modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
        CardHeader("Abitudini", null)
        Spacer(Modifier.height(14.dp))
        val max = s.weekday.maxOrNull()?.coerceAtLeast(1) ?: 1
        val grow = remember(s.weekday) { Animatable(0f) }
        val motion = M.motion
        LaunchedEffect(s.weekday) { if (motion) grow.animateTo(1f, tween(700, 150, Ease.Standard)) else grow.snapTo(1f) }
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            DayOfWeek.entries.forEachIndexed { i, d ->
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
        Line("Giorno preferito", best?.let { Fmt.dayName(DayOfWeek.of(it.index + 1)) } ?: "—")
        Line("Di solito inizi alle", s.typicalStartMin?.let { Fmt.time(it) } ?: "—")
        Line("Fatica media", s.fatigueAvg?.let { "${Fmt.kg(it)}/10" } ?: "—")
        Line("Ripartenze dopo un salto", if (s.skipped > 0) "${s.recovered} su ${s.skipped}" else "nessun salto")
    }
}

@Composable
internal fun TopVideosCard(top: List<Pair<Long, Int>>, videos: List<VideoEntity>, modifier: Modifier) {
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
