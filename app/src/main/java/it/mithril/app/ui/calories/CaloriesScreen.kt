package it.mithril.app.ui.calories

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mithril.app.domain.DayNutrition
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Nutrition
import it.mithril.app.domain.Trend
import it.mithril.app.domain.toDomain
import it.mithril.app.health.Health
import it.mithril.app.health.MealSummary
import it.mithril.app.ui.Dest
import it.mithril.app.ui.LocalNav
import it.mithril.app.ui.Tab
import it.mithril.app.ui.components.Card
import it.mithril.app.ui.components.Divider
import it.mithril.app.ui.components.GrowBar
import it.mithril.app.ui.components.Overline
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.components.RoundIconButton
import it.mithril.app.ui.components.StatValue
import it.mithril.app.ui.components.Symbol
import it.mithril.app.ui.components.TextLink
import it.mithril.app.ui.rememberRepo
import it.mithril.app.ui.statusBarTop
import it.mithril.app.ui.theme.Ease
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T
import it.mithril.app.ui.theme.rise
import it.mithril.app.ui.theme.ts
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class CalRange(val label: String, val days: Int) {
    M1("1M", 30), M3("3M", 91), M6("6M", 182)
}

@Composable
fun CaloriesScreen() {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today by repo.today.collectAsStateWithLifecycle()
    val nutritionEntities by repo.nutrition.collectAsStateWithLifecycle()
    val weightsOrNull by repo.weights.collectAsStateWithLifecycle()
    val settings by repo.appSettings.collectAsStateWithLifecycle()
    val health by Health.state.collectAsStateWithLifecycle()

    val days = remember(nutritionEntities) { nutritionEntities.map { it.toDomain() } }
    val byDay = remember(days) { days.associateBy { it.date } }
    var range by rememberSaveable { mutableStateOf(CalRange.M1) }
    var syncing by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(Health.permissionContract()) {
        scope.launch { Health.refresh(context) }
    }

    LaunchedEffect(health.nutritionGranted, range, today) {
        if (!health.nutritionGranted) return@LaunchedEffect
        Health.syncNutrition(context, today.minusDays(range.days.toLong()), today)
    }

    val summary = remember(days, range, today, settings.caloriesGoal) {
        Nutrition.summarize(days, today.minusDays(range.days - 1L), today, settings.caloriesGoal)
    }
    val todayData = health.todayNutrition ?: byDay[today.toEpochDay()]
    val trend = remember(weightsOrNull, today) {
        val w = weightsOrNull?.map { it.date to it.kg } ?: emptyList()
        Trend.of(w, today.toEpochDay())
    }
    val maintenance = remember(days, trend, today) { Nutrition.estimateMaintenance(days, trend, today) }

    Column(
        Modifier
            .fillMaxSize()
            .background(M.c.bg)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = statusBarTop() + 6.dp, bottom = 28.dp)
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton("arrow_back", { nav.pop() }, size = 44.dp, bg = M.c.bg, contentDescription = "Indietro")
            Text("Calorie", style = T.screenTitle, color = M.c.ink, modifier = Modifier.weight(1f))
            if (health.nutritionGranted) {
                RoundIconButton(
                    "sync",
                    {
                        if (!syncing) {
                            syncing = true
                            scope.launch {
                                Health.refresh(context)
                                Health.syncNutrition(context, today.minusDays(range.days.toLong()), today)
                                syncing = false
                            }
                        }
                    },
                    size = 44.dp, bg = M.c.bg, fg = M.c.ink2, contentDescription = "Aggiorna",
                )
            }
            RoundIconButton("settings", { nav.push(Dest.Settings) }, size = 44.dp, bg = M.c.bg, fg = M.c.ink2, contentDescription = "Impostazioni")
        }

        Column(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                !health.available -> Card(Modifier.rise(0).fillMaxWidth()) {
                    Text("Health Connect non disponibile", style = T.cardTitle, color = M.c.ink)
                    Spacer(Modifier.height(6.dp))
                    Text("Su questo telefono Health Connect non è attivo, quindi Mithril non può importare le calorie da MyFitnessPal.", style = T.body14, color = M.c.ink2)
                }
                !health.nutritionGranted -> Card(Modifier.rise(0).fillMaxWidth()) {
                    Text("Importa da MyFitnessPal", style = T.heroTitle, color = M.c.ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Collega Health Connect per leggere calorie, macro e pasti tracciati su MyFitnessPal. Mithril usa questi dati solo sul telefono per mostrarti media settimanale, equilibrio e andamento insieme al peso.",
                        style = T.body, color = M.c.ink2,
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Collega Health Connect", { launcher.launch(Health.requestSet) }, Modifier.fillMaxWidth(), icon = "local_fire_department", height = 48.dp)
                }
                else -> {
                    TodayCaloriesCard(todayData, settings.caloriesGoal, settings.proteinGoalG, summary.weekAvgKcal, summary.weekTrackedDays, summary.weekDeltaKcal, Modifier.rise(0))
                    TodayMealsCard(health.todayMeals, Modifier.rise(1))
                    if (days.any { it.kcal > 0 }) {
                        TrendCaloriesCard(byDay, today, range, settings.caloriesGoal, summary, Modifier.rise(2)) { range = it }
                    }
                    if (maintenance != null) {
                        MaintenanceCard(maintenance.maintenanceKcal, maintenance.avgIntakeKcal, maintenance.weeklyWeightDeltaKg, maintenance.daysUsed, Modifier.rise(3)) {
                            nav.popToRoot()
                            nav.select(Tab.STATS)
                        }
                    } else if (days.count { it.kcal >= Nutrition.MIN_COMPLETE_KCAL } > 0) {
                        Card(Modifier.rise(3).fillMaxWidth(), radius = 22.dp) {
                            Text("Il tuo equilibrio", style = T.cardTitle, color = M.c.ink)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Dopo almeno 14 giorni tracciati e un trend del peso attivo, qui vedrai la stima reale del tuo mantenimento calorico.",
                                style = T.body14, color = M.c.ink2,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayCaloriesCard(
    todayData: DayNutrition?,
    goalKcal: Int,
    proteinGoalG: Int?,
    weekAvg: Int?,
    weekDays: Int,
    weekDelta: Int?,
    modifier: Modifier,
) {
    val kcal = todayData?.kcal ?: 0
    val left = goalKcal - kcal
    Card(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Oggi", style = T.secondary, color = M.c.ink2)
                if (kcal > 0) StatValue(Fmt.thousands(kcal), "kcal")
                else Text("—", style = T.trendBig, color = M.c.ink3)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 6.dp)) {
                Text("obiettivo ${Fmt.thousands(goalKcal)}", style = T.caption, color = M.c.ink3)
                if (kcal > 0) {
                    Text(
                        if (left >= 0) "${Fmt.thousands(left)} rimanenti" else "+${Fmt.thousands(abs(left))} sopra",
                        style = ts(14, FontWeight.SemiBold),
                        color = if (Nutrition.isOnTarget(kcal, goalKcal)) M.c.ink else M.c.accentInk,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GrowBar(kcal.toFloat() / goalKcal.coerceAtLeast(1), Modifier.fillMaxWidth(), height = 8.dp)
        if (weekAvg != null) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(M.c.surface2).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Settimana (lun–ieri · $weekDays gg)", style = T.caption, color = M.c.ink2)
                    Text("Media ${Fmt.thousands(weekAvg)} kcal/g", style = T.title15, color = M.c.ink)
                }
                if (weekDelta != null) {
                    Text(
                        if (weekDelta >= 0) "margine +${Fmt.thousands(weekDelta)} kcal" else "${Fmt.thousands(abs(weekDelta))} kcal oltre",
                        style = T.captionBold,
                        color = if (weekDelta >= 0) M.c.ink2 else M.c.accentInk,
                    )
                }
            }
        }
        val p = todayData?.proteinG?.roundToInt() ?: 0
        val c = todayData?.carbsG?.roundToInt() ?: 0
        val f = todayData?.fatG?.roundToInt() ?: 0
        if (p + c + f > 0) {
            Spacer(Modifier.height(14.dp))
            MacroBreakdown(p, c, f, proteinGoalG)
        }
    }
}

@Composable
private fun MacroBreakdown(proteinG: Int, carbsG: Int, fatG: Int, proteinGoalG: Int?) {
    val pKcal = proteinG * 4f
    val cKcal = carbsG * 4f
    val fKcal = fatG * 9f
    val total = (pKcal + cKcal + fKcal).coerceAtLeast(1f)
    val pShare = pKcal / total
    val cShare = cKcal / total
    val fShare = fKcal / total
    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
        if (pShare > 0f) Box(Modifier.weight(pShare).fillMaxHeight().background(M.c.accent))
        if (cShare > 0f) Box(Modifier.weight(cShare).fillMaxHeight().background(M.c.accentSoft))
        if (fShare > 0f) Box(Modifier.weight(fShare).fillMaxHeight().background(M.c.ink3.copy(alpha = 0.45f)))
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MacroChip("Proteine", "${proteinG} g", "${(pShare * 100).roundToInt()}%", M.c.accent)
        MacroChip("Carbo", "${carbsG} g", "${(cShare * 100).roundToInt()}%", M.c.accentSoft)
        MacroChip("Grassi", "${fatG} g", "${(fShare * 100).roundToInt()}%", M.c.ink3.copy(alpha = 0.45f))
    }
    if (proteinGoalG != null && proteinGoalG > 0) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Obiettivo proteine", style = T.caption, color = M.c.ink2, modifier = Modifier.weight(1f))
            Text("$proteinG / $proteinGoalG g", style = T.captionBold, color = if (proteinG >= proteinGoalG) M.c.accent else M.c.ink)
        }
        Spacer(Modifier.height(5.dp))
        GrowBar(proteinG.toFloat() / proteinGoalG, Modifier.fillMaxWidth(), height = 5.dp, delayMs = 150)
    }
}

@Composable
private fun MacroChip(label: String, grams: String, pct: String, dot: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Column {
            Text("$label · $pct", style = T.caption, color = M.c.ink3)
            Text(grams, style = T.title15, color = M.c.ink)
        }
    }
}

@Composable
private fun TodayMealsCard(meals: List<MealSummary>, modifier: Modifier) {
    Card(modifier.fillMaxWidth(), radius = 22.dp) {
        Overline("Pasti di oggi")
        Spacer(Modifier.height(8.dp))
        if (meals.isEmpty()) {
            Text(
                "Nessun pasto registrato oggi su Health Connect. Quando aggiungi un alimento su MyFitnessPal, compare qui al prossimo aggiornamento.",
                style = T.body14, color = M.c.ink2,
            )
            return@Card
        }
        meals.forEachIndexed { i, m ->
            if (i > 0) Divider(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.kind.label, style = T.title15, color = M.c.ink)
                    val macros = listOfNotNull(
                        m.proteinG?.roundToInt()?.let { "P ${it}g" },
                        m.carbsG?.roundToInt()?.let { "C ${it}g" },
                        m.fatG?.roundToInt()?.let { "G ${it}g" },
                    ).joinToString(" · ")
                    if (macros.isNotEmpty()) Text(macros, style = T.caption, color = M.c.ink3)
                }
                Text("${Fmt.thousands(m.kcal)} kcal", style = ts(15, FontWeight.SemiBold), color = M.c.ink)
            }
        }
    }
}

@Composable
private fun TrendCaloriesCard(
    byDay: Map<Long, DayNutrition>,
    today: LocalDate,
    range: CalRange,
    goalKcal: Int,
    summary: it.mithril.app.domain.CaloriePeriodSummary,
    modifier: Modifier,
    onRange: (CalRange) -> Unit,
) {
    val sub = summary.avgKcal?.let {
        "media ${Fmt.thousands(it)} kcal/g · ${summary.onTargetDays} di ${summary.daysTracked} gg in target"
    }
    Card(modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
        Text("Andamento", style = T.cardTitle, color = M.c.ink)
        if (sub != null) Text(sub, style = T.secondary, color = M.c.ink2)
        Spacer(Modifier.height(12.dp))
        SegmentedRange(range, onRange)
        Spacer(Modifier.height(8.dp))
        CaloriesChart(
            days = (today.minusDays(range.days - 1L).toEpochDay()..today.toEpochDay()).toList(),
            byDay = byDay,
            today = today,
            goalKcal = goalKcal,
            modifier = Modifier.fillMaxWidth().height(190.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(M.c.accent)); Spacer(Modifier.width(5.dp)); Text("in target", style = T.caption, color = M.c.ink2) }
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(M.c.accentSoft)); Spacer(Modifier.width(5.dp)); Text("sopra", style = T.caption, color = M.c.ink2) }
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.width(14.dp).height(2.dp).background(M.c.ink)); Spacer(Modifier.width(5.dp)); Text("media 7 gg", style = T.caption, color = M.c.ink2) }
        }
    }
}

@Composable
private fun SegmentedRange(selected: CalRange, onSelect: (CalRange) -> Unit) {
    Row(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(18.dp)).background(M.c.surface2).padding(3.dp)) {
        CalRange.entries.forEach { r ->
            val sel = r == selected
            val bg by animateColorAsState(if (sel) M.c.surface else M.c.surface2, tween(250), label = "calSeg")
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .then(if (sel && !M.c.dark) Modifier.shadow(2.dp, RoundedCornerShape(15.dp), ambientColor = Color(0x223C281E), spotColor = Color(0x223C281E)) else Modifier)
                    .clip(RoundedCornerShape(15.dp)).background(bg).clickable { onSelect(r) },
                contentAlignment = Alignment.Center,
            ) { Text(r.label, style = ts(13, if (sel) FontWeight.SemiBold else FontWeight.Medium), color = if (sel) M.c.ink else M.c.ink2) }
        }
    }
}

@Composable
private fun CaloriesChart(
    days: List<Long>,
    byDay: Map<Long, DayNutrition>,
    today: LocalDate,
    goalKcal: Int,
    modifier: Modifier,
) {
    if (days.isEmpty()) return
    val c = M.c
    val measurer = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val kcalMap = remember(days, byDay) { days.mapNotNull { d -> byDay[d]?.kcal?.takeIf { it > 0 }?.let { d to it } }.toMap() }
    val rolling = remember(days, kcalMap) { Nutrition.rollingKcal(days, kcalMap) }
    val grow = remember(days.size) { Animatable(0f) }
    val motion = M.motion
    LaunchedEffect(days.size) { if (motion) grow.animateTo(1f, tween(800, 150, Ease.Standard)) else grow.snapTo(1f) }
    val top = ((kcalMap.values + goalKcal).maxOrNull() ?: goalKcal).toFloat() * 1.08f
    val labelStyle = T.caption.copy(color = c.ink3, fontSize = T.caption.fontSize * 0.92f)
    val pillStyle = T.captionBold.copy(color = c.accentInk)
    val labelWPx = maxOf(with(density) { 36.dp.toPx() }, measurer.measure("4.000", labelStyle).size.width + with(density) { 8.dp.toPx() })
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var scrubIdx by remember(days.size) { mutableStateOf<Int?>(null) }

    Canvas(
        modifier
            .onSizeChanged { canvasWidth = it.width.toFloat() }
            .pointerInput(days) {
                val touchSlop = viewConfiguration.touchSlop
                fun idxAt(x: Float): Int {
                    val plotW = (canvasWidth - labelWPx).coerceAtLeast(1f)
                    val frac = (x / plotW).coerceIn(0f, 0.9999f)
                    return (frac * days.size).toInt().coerceIn(0, days.lastIndex)
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val sx = down.position.x
                    val sy = down.position.y
                    var mode = 0
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            if (mode == 0) {
                                val t = idxAt(ch.position.x)
                                scrubIdx = if (scrubIdx == t) null else t
                                if (scrubIdx != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (mode == 1) {
                                scrubIdx = null
                            }
                            break
                        }
                        val dx = ch.position.x - sx
                        val dy = ch.position.y - sy
                        if (mode == 0) {
                            if (abs(dx) > touchSlop && abs(dx) >= abs(dy)) {
                                mode = 1; ch.consume()
                                val i = idxAt(ch.position.x)
                                if (i != scrubIdx) { scrubIdx = i; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            } else if (abs(dy) > touchSlop) { mode = -1; break }
                        } else if (mode == 1) {
                            ch.consume()
                            val i = idxAt(ch.position.x)
                            if (i != scrubIdx) { scrubIdx = i; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                        }
                    }
                }
            }
    ) {
        val topH = 26.dp.toPx()
        val bottomH = 18.dp.toPx()
        val w = (size.width - labelWPx).coerceAtLeast(1f)
        val h = (size.height - topH - bottomH).coerceAtLeast(1f)
        val slot = w / days.size
        val bw = (slot * 0.7f).coerceAtLeast(1f)
        fun y(v: Float) = topH + h - v / top * h

        days.forEachIndexed { i, d ->
            val v = kcalMap[d] ?: return@forEachIndexed
            val vh = v * grow.value
            val col = if (Nutrition.isOnTarget(v, goalKcal)) c.accent else c.accentSoft
            drawRoundRect(col, Offset(slot * i + (slot - bw) / 2, y(vh)), Size(bw, topH + h - y(vh)), CornerRadius(minOf(bw / 2, 3.dp.toPx())))
        }

        val gy = y(goalKcal.toFloat())
        drawLine(c.ink3, Offset(0f, gy), Offset(w, gy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
        val gt = measurer.measure(Fmt.thousands(goalKcal), labelStyle)
        drawText(gt, topLeft = Offset(w + 6.dp.toPx(), (gy - gt.size.height / 2).coerceIn(topH, topH + h - gt.size.height)))

        val p = Path(); var started = false
        rolling.forEachIndexed { i, v ->
            if (v == null) { started = false; return@forEachIndexed }
            val cx = slot * i + slot / 2
            val yy = y(v * grow.value)
            if (!started) { p.moveTo(cx, yy); started = true } else p.lineTo(cx, yy)
        }
        drawPath(p, c.ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        listOf(0, days.size / 2, days.size - 1).distinct().forEach { i ->
            val lbl = if (days[i] == today.toEpochDay()) "oggi" else Fmt.dateShort(LocalDate.ofEpochDay(days[i]), today)
            val txt = measurer.measure(lbl, labelStyle)
            val cx = slot * i + slot / 2
            drawText(txt, topLeft = Offset((cx - txt.size.width / 2f).coerceIn(0f, (w - txt.size.width).coerceAtLeast(0f)), topH + h + 4.dp.toPx()))
        }

        val si = scrubIdx
        if (si != null && si in days.indices) {
            val d = days[si]
            val sx = slot * si + slot / 2
            drawLine(c.ink3.copy(alpha = 0.5f), Offset(sx, topH), Offset(sx, topH + h), 1.dp.toPx())
            val entry = byDay[d]
            val dateLbl = if (d == today.toEpochDay()) "oggi" else Fmt.dateShort(LocalDate.ofEpochDay(d), today)
            val txtStr = if (entry != null && entry.kcal > 0) {
                val p = entry.proteinG?.roundToInt()?.let { "P${it}" }
                val cb = entry.carbsG?.roundToInt()?.let { "C${it}" }
                val f = entry.fatG?.roundToInt()?.let { "G${it}" }
                val m = listOfNotNull(p, cb, f).joinToString(" ")
                if (m.isNotEmpty()) "$dateLbl · ${Fmt.thousands(entry.kcal)} kcal · $m" else "$dateLbl · ${Fmt.thousands(entry.kcal)} kcal"
            } else "$dateLbl · non tracciato"
            val measured = measurer.measure(txtStr, pillStyle, maxLines = 1)
            val padX = 9.dp.toPx()
            val pillW = measured.size.width + padX * 2
            val pillH = 22.dp.toPx()
            val pillX = (sx - pillW / 2f).coerceIn(0f, (size.width - pillW).coerceAtLeast(0f))
            drawRoundRect(c.accentSoft, Offset(pillX, 0f), Size(pillW, pillH), CornerRadius(pillH / 2f))
            drawText(measured, topLeft = Offset(pillX + padX, (pillH - measured.size.height) / 2f))
        }
    }
}

@Composable
private fun MaintenanceCard(
    maintenanceKcal: Int,
    avgIntake: Int,
    weeklyDeltaKg: Double,
    daysUsed: Int,
    modifier: Modifier,
    onStats: () -> Unit,
) {
    Card(modifier.fillMaxWidth(), radius = 22.dp) {
        Overline("Il tuo equilibrio (ultimi $daysUsed gg tracciati)")
        Spacer(Modifier.height(6.dp))
        StatValue(Fmt.thousands(maintenanceKcal), "kcal/giorno stimate")
        Spacer(Modifier.height(8.dp))
        Text(
            "Con una media di ${Fmt.thousands(avgIntake)} kcal/g il tuo peso varia di ${Fmt.signedRate(weeklyDeltaKg)} kg/sett.: il mantenimento reale è intorno a ${Fmt.thousands(maintenanceKcal)} kcal.",
            style = T.body14, color = M.c.ink2,
        )
        Spacer(Modifier.height(8.dp))
        TextLink("Correlazione con allenamenti e peso", onStats)
    }
}
