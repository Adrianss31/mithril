package it.mithril.app.ui.weight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import it.mithril.app.ui.components.drawTrimmed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Trend
import it.mithril.app.ui.theme.Ease
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T

enum class Range(val label: String, val days: Int, val dot: Float) {
    M1("1M", 30, 2.2f), M3("3M", 91, 1.6f), M6("6M", 182, 1.6f), Y1("1A", 365, 1.2f)
}

/**
 * Grafico del peso: pesate come punti, trend come linea che si disegna, verticale "oggi",
 * proiezione tratteggiata fino all'obiettivo (al massimo il 40% del periodo in avanti) e linea obiettivo.
 */
@Composable
fun WeightChart(trend: Trend, today: LocalDate, range: Range, goalKg: Double?, slopePerDay: Double?, modifier: Modifier = Modifier) {
    val c = M.c
    val motion = M.motion
    val measurer = rememberTextMeasurer()
    val draw = remember(range) { Animatable(if (motion) 0f else 1f) }
    val dots = remember(range) { Animatable(if (motion) 0f else 1f) }
    val late = remember(range) { Animatable(if (motion) 0f else 1f) }
    val pop = remember(range) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(range) {
        if (!motion) return@LaunchedEffect
        launch { draw.animateTo(1f, tween(1200, 200, Ease.Draw)) }
        launch { dots.animateTo(1f, tween(600)) }
        launch { late.animateTo(1f, tween(500, 1300)) }
        launch { delay(1300); pop.animateTo(1f, spring(0.55f, 400f)) }
    }
    val t = today.toEpochDay()
    val from = t - range.days
    val now = trend.at(t)
    // Proiezione verso l'obiettivo, se il trend ci sta andando.
    val projDays: Int = if (goalKg != null && now != null && slopePerDay != null && (goalKg - now) * slopePerDay > 0 && kotlin.math.abs(slopePerDay) > 0.002) {
        min(ceil((goalKg - now) / slopePerDay).toInt(), (range.days * 0.4).toInt())
    } else 0
    val to = t + max(projDays, (range.days * 0.05).toInt().coerceAtLeast(1))
    val weighIns = trend.weighIns.filter { it.first in from..t }
    val trendPts = (max(from, trend.first)..t).mapNotNull { d -> trend.at(d)?.let { d to it } }
    val projEnd = if (projDays > 0 && now != null) now + slopePerDay!! * projDays else null
    val values = weighIns.map { it.second } + trendPts.map { it.second } + listOfNotNull(projEnd) +
        (if (goalKg != null && projDays > 0) listOf(goalKg) else emptyList())
    if (values.isEmpty()) return
    var lo = values.min(); var hi = values.max()
    if (hi - lo < 1.0) { val m = (hi + lo) / 2; lo = m - 0.5; hi = m + 0.5 }
    val pad = (hi - lo) * 0.08; lo -= pad; hi += pad
    val labelStyle = T.caption.copy(color = c.ink3, fontSize = T.caption.fontSize * 0.92f)
    val xLabels = (0..3).map { k -> if (k == 3) "oggi" to t else (from + (t - from) * k / 3).let { d -> Fmt.dateShort(LocalDate.ofEpochDay(d)) to d } }

    Canvas(modifier) {
        val labelW = 34.dp.toPx()
        val bottomH = 20.dp.toPx()
        val w = size.width - labelW
        val h = size.height - bottomH
        fun x(d: Long) = (d - from).toFloat() / (to - from) * w
        fun y(v: Double) = ((hi - v) / (hi - lo)).toFloat() * h

        // Etichette Y (3) a destra
        for (k in 0..2) {
            val v = hi - pad - (hi - lo - 2 * pad) * k / 2
            val txt = measurer.measure(Fmt.kg(v), labelStyle)
            drawText(txt, topLeft = Offset(w + 8.dp.toPx(), (y(v) - txt.size.height / 2).coerceIn(0f, h - txt.size.height)))
        }
        // Etichette X (4)
        xLabels.forEachIndexed { k, (label, d) ->
            val txt = measurer.measure(label, labelStyle)
            val cx = x(d) - if (k == 3) txt.size.width / 2f else 0f
            drawText(txt, topLeft = Offset(cx.coerceIn(0f, w - txt.size.width / 2f), h + 4.dp.toPx()))
        }
        // Obiettivo
        if (goalKg != null && goalKg in lo..hi) {
            val gy = y(goalKg)
            drawLine(c.ink3, Offset(0f, gy), Offset(w, gy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
            val txt = measurer.measure("obiettivo ${Fmt.kg(goalKg)}", labelStyle)
            drawText(txt, topLeft = Offset(4.dp.toPx(), gy - txt.size.height - 2.dp.toPx()))
        }
        // Oggi
        drawLine(c.line2, Offset(x(t), 0f), Offset(x(t), h), 1.dp.toPx())
        // Pesate
        weighIns.forEach { (d, v) -> drawCircle(c.ink3.copy(alpha = 0.55f * dots.value), range.dot.dp.toPx(), Offset(x(d), y(v))) }
        // Trend
        if (trendPts.size >= 2) {
            val p = Path()
            trendPts.forEachIndexed { i, (d, v) -> if (i == 0) p.moveTo(x(d), y(v)) else p.lineTo(x(d), y(v)) }
            drawTrimmed(p, draw.value, c.accent, 3.dp.toPx())
        }
        // Proiezione
        if (projEnd != null && now != null) {
            drawLine(
                c.accent.copy(alpha = 0.7f * late.value), Offset(x(t), y(now)), Offset(x(t + projDays), y(projEnd)), 2.dp.toPx(),
                cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
            )
        }
        // Punto di oggi
        if (now != null && pop.value > 0f) {
            val r = 5.dp.toPx() * pop.value
            drawCircle(c.surface, r + 2.dp.toPx() * pop.value, Offset(x(t), y(now)))
            drawCircle(c.accent, r, Offset(x(t), y(now)))
        }
    }
}
