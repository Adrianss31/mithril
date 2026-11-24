package it.mithril.app.ui.stats

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.mithril.app.data.SessionLogEntity
import it.mithril.app.data.SlotEntity
import it.mithril.app.data.VideoEntity
import it.mithril.app.domain.DayNutrition
import it.mithril.app.domain.DayState
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Nutrition
import it.mithril.app.domain.Stats
import it.mithril.app.domain.Trend
import it.mithril.app.domain.Week
import it.mithril.app.ui.components.Card
import it.mithril.app.ui.components.RoundIconButton
import it.mithril.app.ui.components.Symbol
import it.mithril.app.ui.components.dashedBorder
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T
import it.mithril.app.ui.theme.ts
import it.mithril.app.widget.formatSteps
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

internal enum class CalMode(val label: String) {
    ALL("Tutto"), STEPS("Passi"), WEIGHT("Peso"), CALORIES("Calorie")
}

@Composable
internal fun <E> Segmented(items: List<E>, selected: E, label: (E) -> String, onSelect: (E) -> Unit, height: Int = 40) {
    Row(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape((height / 2).dp)).background(M.c.surface2).padding(3.dp)) {
        items.forEach { r ->
            val sel = r == selected
            val bg by animateColorAsState(if (sel) M.c.surface else M.c.surface2, tween(250), label = "seg")
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .then(if (sel && !M.c.dark) Modifier.shadow(2.dp, RoundedCornerShape(((height - 6) / 2).dp), ambientColor = Color(0x223C281E), spotColor = Color(0x223C281E)) else Modifier)
                    .clip(RoundedCornerShape(((height - 6) / 2).dp)).background(bg).clickable { onSelect(r) },
                contentAlignment = Alignment.Center,
            ) { Text(label(r), style = ts(12, if (sel) FontWeight.SemiBold else FontWeight.Medium), color = if (sel) M.c.ink else M.c.ink2, maxLines = 1) }
        }
    }
}

@Composable
internal fun CalendarCard(
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
    nutritionByDay: Map<Long, DayNutrition>,
    caloriesGoal: Int,
    videos: List<VideoEntity>,
    modifier: Modifier,
) {
    val c = M.c
    val weighIns = remember(weights) { weights.toMap() }
    val first = remember(logs, weights, slots, nutritionByDay) {
        listOfNotNull(
            logs.minOfOrNull { it.date },
            weights.minOfOrNull { it.first },
            slots.minOfOrNull { it.date },
            nutritionByDay.keys.minOrNull(),
        ).minOrNull()
    }
    val canBack = first != null && month.atDay(1).toEpochDay() > first
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
            DayOfWeek.entries.forEach { d ->
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
                            mode, steps[d]?.takeIf { it > 0 }, stepsGoal, weighIns[d], nutritionByDay[d]?.kcal?.takeIf { it > 0 }, caloriesGoal, Modifier.weight(1f),
                        ) { onSelect(d) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        CalendarLegend(mode)
        Spacer(Modifier.height(14.dp))
        DayDetail(LocalDate.ofEpochDay(selected), today, slots, logs, weighIns[selected], trend, steps[selected]?.takeIf { it > 0 }, stepsGoal, nutritionByDay[selected], caloriesGoal, videos)
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
    kcal: Int?,
    caloriesGoal: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = M.c
    val shape = RoundedCornerShape(12.dp)
    val stepsF = steps?.let { (it.toFloat() / stepsGoal).coerceIn(0f, 1f) } ?: 0f
    val calOnTarget = kcal != null && Nutrition.isOnTarget(kcal, caloriesGoal)
    val bg = when (mode) {
        CalMode.ALL -> when (state) { DayState.DONE -> c.accent; DayState.MINI -> c.accentSoft; else -> Color.Transparent }
        CalMode.STEPS -> if (steps != null) c.accent.copy(alpha = 0.10f + 0.55f * stepsF) else Color.Transparent
        CalMode.WEIGHT -> if (kg != null) c.surface2 else Color.Transparent
        CalMode.CALORIES -> if (kcal != null) (if (calOnTarget) c.accent.copy(alpha = 0.22f) else c.accentSoft) else Color.Transparent
    }
    val strong = mode == CalMode.ALL && state == DayState.DONE || mode == CalMode.STEPS && stepsF >= 1f
    val ink = when {
        strong -> c.onAccent
        mode == CalMode.ALL && state == DayState.MINI -> c.accentInk
        mode == CalMode.CALORIES && kcal != null && !calOnTarget -> c.accentInk
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
            CalMode.CALORIES -> if (kcal != null) Text("${kcal}", style = ts(10, FontWeight.SemiBold), color = ink, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), maxLines = 1)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarLegend(mode: CalMode) {
    val c = M.c
    @Composable
    fun item(label: String, swatch: @Composable () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
        swatch(); Spacer(Modifier.width(5.dp)); Text(label, style = T.caption, color = c.ink2)
    }
    val box = Modifier.size(12.dp).clip(RoundedCornerShape(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            CalMode.CALORIES -> {
                item("in target") { Box(box.background(c.accent.copy(alpha = 0.22f))) }
                item("sopra obiettivo") { Box(box.background(c.accentSoft)) }
            }
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
    nutrition: DayNutrition?,
    caloriesGoal: Int,
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
        if (nutrition != null && nutrition.kcal > 0) {
            DetailRow("local_fire_department") {
                val macros = listOfNotNull(
                    nutrition.proteinG?.roundToInt()?.let { "P ${it}g" },
                    nutrition.carbsG?.roundToInt()?.let { "C ${it}g" },
                    nutrition.fatG?.roundToInt()?.let { "G ${it}g" },
                ).joinToString(" ")
                val status = if (Nutrition.isOnTarget(nutrition.kcal, caloriesGoal)) "in target" else "sopra"
                Text(
                    "${Fmt.thousands(nutrition.kcal)} kcal ($status)" + if (macros.isNotEmpty()) " · $macros" else "",
                    style = T.body14, color = c.ink,
                )
            }
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
