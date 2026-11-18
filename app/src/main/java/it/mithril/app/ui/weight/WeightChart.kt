package it.mithril.app.ui.weight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import it.mithril.app.ui.components.drawTrimmed
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Projection
import it.mithril.app.domain.Trend
import it.mithril.app.ui.theme.Ease
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T

enum class Range(val label: String, val days: Int, val dot: Float) {
    M1("1M", 30, 2.2f), M3("3M", 91, 1.6f), M6("6M", 182, 1.6f), Y1("1A", 365, 1.2f)
}

/** Giorno selezionato dal dito sul grafico; nelle viste larghe aggancia i puntini vicini. */
internal fun scrubDayForX(
    xPx: Float,
    plotWidthPx: Float,
    from: Long,
    to: Long,
    today: Long,
    minDay: Long,
    maxDay: Long,
    weighInDays: List<Long>,
    snapRadiusPx: Float,
    denseCutoffPx: Float,
): Long {
    val safeMin = minOf(minDay, today)
    val safeMax = maxOf(today, maxDay)
    if (plotWidthPx <= 0f || to <= from) return today.coerceIn(safeMin, safeMax)
    val clampedX = xPx.coerceIn(0f, plotWidthPx)
    val exactDay = from + (clampedX / plotWidthPx) * (to - from).toDouble()
    val pxPerDay = plotWidthPx / (to - from).toFloat()
    if (exactDay <= today + 0.5) {
        if (pxPerDay < denseCutoffPx && weighInDays.isNotEmpty()) {
            val closest = weighInDays.minByOrNull { abs(it - exactDay) }
            if (closest != null && abs(closest - exactDay) * pxPerDay <= snapRadiusPx) {
                return closest.coerceIn(safeMin, today)
            }
        }
        return exactDay.roundToLong().coerceIn(safeMin, today)
    }
    if (safeMax <= today) return today.coerceIn(safeMin, today)
    return exactDay.roundToLong().coerceIn(today + 1, safeMax)
}

/** Valore del trend (o della proiezione futura) nel giorno indicato: (kg, èProiezione). */
internal fun scrubValueAt(day: Long, trend: Trend, today: Long, now: Double?, proj: Projection?): Pair<Double, Boolean>? = when {
    day <= today -> trend.at(day)?.let { it to false }
    proj != null && now != null -> {
        val v = if (proj.reachesGoal && day >= today + proj.days) proj.endKg else now + proj.slopePerDay * (day - today)
        v to true
    }
    else -> null
}

/**
 * Grafico del peso: pesate come punti, trend come linea che si disegna, verticale "oggi", linea obiettivo
 * e ispezione interattiva trascinando o toccando il grafico.
 * Con [forecast] oggi sta nel mezzo: a sinistra il periodo scelto, a destra la proiezione per altrettanti giorni
 * (si ferma prima se arriva all'obiettivo). Senza, il grafico mostra solo il passato.
 */
@Composable
fun WeightChart(trend: Trend, today: LocalDate, range: Range, goalKg: Double?, modifier: Modifier = Modifier, forecast: Boolean = true) {
    val c = M.c
    val motion = M.motion
    val measurer = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val draw = remember(range) { Animatable(if (motion) 0f else 1f) }
    val dots = remember(range) { Animatable(if (motion) 0f else 1f) }
    val late = remember(range) { Animatable(if (motion) 0f else 1f) }
    val pop = remember(range) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(range) {
        if (!motion) return@LaunchedEffect
        launch { draw.animateTo(1f, tween(1200, 200, Ease.Draw)) }
        launch { dots.animateTo(1f, tween(600)) }
        launch { late.animateTo(1f, tween(500, 1300)) }
        launch { kotlinx.coroutines.delay(1300); pop.animateTo(1f, spring(0.55f, 400f)) }
    }
    val t = today.toEpochDay()
    val from = t - range.days
    val to = if (forecast) t + range.days else t + (range.days * 0.05).toInt().coerceAtLeast(1)
    val now = trend.at(t)
    val proj = if (forecast) trend.projection(t, range.days, goalKg) else null
    val weighIns = remember(trend, from, t) { trend.weighIns.filter { it.first in from..t } }
    val weighInMap = remember(weighIns) { weighIns.associate { it.first to it.second } }
    val weighInDays = remember(weighIns) { weighIns.map { it.first } }
    val minDay = max(from, trend.first)
    val maxDay = if (proj != null) t + proj.days else t
    val trendPts = remember(trend, from, t) { (max(from, trend.first)..t).mapNotNull { d -> trend.at(d)?.let { d to it } } }
    // L'obiettivo entra nella scala solo se la proiezione ci arriva: altrimenti schiaccerebbe il trend in una riga.
    val values = weighIns.map { it.second } + trendPts.map { it.second } + listOfNotNull(proj?.endKg)
    if (values.isEmpty()) return
    var lo = values.min(); var hi = values.max()
    if (hi - lo < 1.0) { val m = (hi + lo) / 2; lo = m - 0.5; hi = m + 0.5 }
    val pad = (hi - lo) * 0.08; lo -= pad; hi += pad
    val labelStyle = T.caption.copy(color = c.ink3, fontSize = T.caption.fontSize * 0.92f)
    val valueStyle = T.caption.copy(color = c.accentInk, fontWeight = FontWeight.SemiBold)
    val pillStyle = T.captionBold.copy(color = c.accentInk)
    // Etichette X: (testo, giorno, allineamento 0 = a sinistra, 0,5 = centrata, 1 = a destra)
    val xLabels = if (forecast) listOf(
        Triple(Fmt.dateShort(LocalDate.ofEpochDay(from), today), from, 0f), Triple("oggi", t, 0.5f), Triple(Fmt.dateShort(LocalDate.ofEpochDay(to), today), to, 1f),
    ) else (0..3).map { k -> if (k == 3) Triple("oggi", t, 0.5f) else (from + (t - from) * k / 3).let { d -> Triple(Fmt.dateShort(LocalDate.ofEpochDay(d)), d, 0f) } }

    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var scrubDay by remember(range, trend, forecast) { mutableStateOf<Long?>(null) }
    val labelWPx = with(density) { 34.dp.toPx() }
    val snapRadiusPx = with(density) { 6.dp.toPx() }
    val denseCutoffPx = with(density) { 3.5.dp.toPx() }

    Canvas(
        modifier
            .onSizeChanged { canvasWidth = it.width.toFloat() }
            .pointerInput(from, to, t, minDay, maxDay, weighInDays, denseCutoffPx, snapRadiusPx) {
                val touchSlop = viewConfiguration.touchSlop
                fun dayAt(x: Float): Long = scrubDayForX(
                    xPx = x,
                    plotWidthPx = (canvasWidth - labelWPx).coerceAtLeast(1f),
                    from = from,
                    to = to,
                    today = t,
                    minDay = minDay,
                    maxDay = maxDay,
                    weighInDays = weighInDays,
                    snapRadiusPx = snapRadiusPx,
                    denseCutoffPx = denseCutoffPx,
                )
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
                    var mode = 0 // 0 = attesa, 1 = scrub orizzontale, -1 = scroll verticale
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (mode == 0) {
                                val plotW = (canvasWidth - labelWPx).coerceAtLeast(1f)
                                if (change.position.x <= plotW + labelWPx * 0.25f) {
                                    val tapped = dayAt(change.position.x)
                                    scrubDay = if (scrubDay == tapped) null else tapped
                                    if (scrubDay != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } else {
                                    scrubDay = null
                                }
                            } else if (mode == 1) {
                                scrubDay = null
                            }
                            break
                        }
                        val dx = change.position.x - startX
                        val dy = change.position.y - startY
                        if (mode == 0) {
                            if (abs(dx) > touchSlop && abs(dx) >= abs(dy)) {
                                mode = 1
                                change.consume()
                                val d = dayAt(change.position.x)
                                if (d != scrubDay) {
                                    scrubDay = d
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            } else if (abs(dy) > touchSlop) {
                                mode = -1
                                break
                            }
                        } else if (mode == 1) {
                            change.consume()
                            val d = dayAt(change.position.x)
                            if (d != scrubDay) {
                                scrubDay = d
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                }
            }
    ) {
        val labelW = 34.dp.toPx()
        val topH = 28.dp.toPx()
        val bottomH = 20.dp.toPx()
        val w = size.width - labelW
        val h = (size.height - topH - bottomH).coerceAtLeast(1f)
        fun x(d: Long) = (d - from).toFloat() / (to - from) * w
        fun y(v: Double) = topH + ((hi - v) / (hi - lo)).toFloat() * h

        // Metà futura appena velata
        if (forecast) drawRoundRect(c.surface2.copy(alpha = 0.5f), Offset(x(t), topH), Size(w - x(t), h), CornerRadius(8.dp.toPx()))
        // Etichette Y (3) a destra
        for (k in 0..2) {
            val v = hi - pad - (hi - lo - 2 * pad) * k / 2
            // Il peso previsto ha già la sua etichetta in fondo alla proiezione: niente doppione sull'asse.
            if (proj != null && abs(y(v) - y(proj.endKg)) < 14.dp.toPx()) continue
            val txt = measurer.measure(Fmt.kg(v), labelStyle)
            drawText(txt, topLeft = Offset(w + 8.dp.toPx(), (y(v) - txt.size.height / 2).coerceIn(topH, topH + h - txt.size.height)))
        }
        // Etichette X
        xLabels.forEach { (label, d, align) ->
            val txt = measurer.measure(label, labelStyle)
            val cx = x(d) - txt.size.width * align
            drawText(txt, topLeft = Offset(cx.coerceIn(0f, w - txt.size.width), topH + h + 4.dp.toPx()))
        }
        // Obiettivo: linea se è in scala, altrimenti una freccia sul bordo verso cui si trova
        if (goalKg != null) {
            if (goalKg in lo..hi) {
                val gy = y(goalKg)
                drawLine(c.ink3, Offset(0f, gy), Offset(w, gy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
                val txt = measurer.measure("obiettivo ${Fmt.kg(goalKg)}", labelStyle)
                drawText(txt, topLeft = Offset(4.dp.toPx(), if (gy - txt.size.height - 2.dp.toPx() >= topH) gy - txt.size.height - 2.dp.toPx() else gy + 2.dp.toPx()))
            } else {
                val txt = measurer.measure("obiettivo ${Fmt.kg(goalKg)} ${if (goalKg < lo) "↓" else "↑"}", labelStyle)
                drawText(txt, topLeft = Offset(4.dp.toPx(), if (goalKg < lo) topH + h - txt.size.height - 2.dp.toPx() else topH + 2.dp.toPx()))
            }
        }
        // Oggi
        drawLine(c.line2, Offset(x(t), topH), Offset(x(t), topH + h), 1.dp.toPx())
        // Pesate
        weighIns.forEach { (d, v) -> drawCircle(c.ink3.copy(alpha = 0.55f * dots.value), range.dot.dp.toPx(), Offset(x(d), y(v))) }
        // Trend
        if (trendPts.size >= 2) {
            val p = Path()
            trendPts.forEachIndexed { i, (d, v) -> if (i == 0) p.moveTo(x(d), y(v)) else p.lineTo(x(d), y(v)) }
            drawTrimmed(p, draw.value, c.accent, 3.dp.toPx())
        }
        // Proiezione, con il peso previsto all'arrivo
        if (proj != null && now != null) {
            val end = Offset(x(t + proj.days), y(proj.endKg))
            drawLine(
                c.accent.copy(alpha = 0.7f * late.value), Offset(x(t), y(now)), end, 2.dp.toPx(),
                cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
            )
            if (late.value > 0f) {
                drawCircle(c.surface.copy(alpha = late.value), 4.dp.toPx(), end)
                drawCircle(c.accent.copy(alpha = 0.8f * late.value), 4.dp.toPx(), end, style = Stroke(2.dp.toPx()))
                val txt = measurer.measure(Fmt.kg(proj.endKg), valueStyle.copy(color = valueStyle.color.copy(alpha = late.value)))
                val above = end.y - txt.size.height - 6.dp.toPx() >= topH
                drawText(
                    txt,
                    topLeft = Offset(
                        (end.x - txt.size.width / 2f).coerceIn(x(t) + 4.dp.toPx(), w - txt.size.width - 2.dp.toPx()),
                        if (above) end.y - txt.size.height - 6.dp.toPx() else end.y + 6.dp.toPx(),
                    ),
                )
            }
        }
        // Punto di oggi
        if (now != null && pop.value > 0f) {
            val r = 5.dp.toPx() * pop.value
            drawCircle(c.surface, r + 2.dp.toPx() * pop.value, Offset(x(t), y(now)))
            drawCircle(c.accent, r, Offset(x(t), y(now)))
        }
        // Ispezione col dito (scrubbing)
        val sd = scrubDay
        val sv = sd?.let { scrubValueAt(it, trend, t, now, proj) }
        if (sd != null && sv != null) {
            val (valKg, isProj) = sv
            val sx = x(sd)
            val sy = y(valKg)
            val weighVal = if (!isProj) weighInMap[sd] else null
            drawLine(c.ink3.copy(alpha = 0.45f), Offset(sx, topH), Offset(sx, topH + h), 1.dp.toPx())
            if (weighVal != null) {
                val wy = y(weighVal)
                drawCircle(c.surface, 5.dp.toPx(), Offset(sx, wy))
                drawCircle(c.ink2, 3.5.dp.toPx(), Offset(sx, wy))
            }
            drawCircle(c.surface, 6.5.dp.toPx(), Offset(sx, sy))
            if (isProj) {
                drawCircle(c.accent, 4.5.dp.toPx(), Offset(sx, sy), style = Stroke(2.dp.toPx()))
            } else {
                drawCircle(c.accent, 4.5.dp.toPx(), Offset(sx, sy))
            }
            val dateLabel = if (sd == t) "oggi" else Fmt.dateShort(LocalDate.ofEpochDay(sd), today)
            val fullText = when {
                isProj -> "$dateLabel · stima ~${Fmt.kg(valKg)} kg"
                weighVal != null -> "$dateLabel · trend ${Fmt.kg(valKg)} · pesata ${Fmt.kg(weighVal)}"
                else -> "$dateLabel · trend ${Fmt.kg(valKg)} kg"
            }
            val padX = 9.dp.toPx()
            val measuredFull = measurer.measure(fullText, pillStyle, maxLines = 1)
            val txt = if (measuredFull.size.width + padX * 2 <= size.width || weighVal == null) {
                measuredFull
            } else {
                measurer.measure("$dateLabel · ${Fmt.kg(valKg)} / ${Fmt.kg(weighVal)}", pillStyle, maxLines = 1)
            }
            val pillW = txt.size.width + padX * 2
            val pillH = 22.dp.toPx()
            val pillX = (sx - pillW / 2f).coerceIn(0f, (size.width - pillW).coerceAtLeast(0f))
            val pillY = ((topH - 4.dp.toPx() - pillH) / 2f).coerceAtLeast(0f)
            drawRoundRect(c.accentSoft, Offset(pillX, pillY), Size(pillW, pillH), CornerRadius(pillH / 2f))
            drawText(txt, topLeft = Offset(pillX + padX, pillY + (pillH - txt.size.height) / 2f))
        }
    }
}
