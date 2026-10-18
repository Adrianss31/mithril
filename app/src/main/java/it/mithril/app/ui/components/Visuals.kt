package it.mithril.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import it.mithril.app.ui.theme.dotIn
import it.mithril.app.ui.theme.ts
import it.mithril.app.domain.DayInfo
import it.mithril.app.domain.DayState
import it.mithril.app.domain.Fmt
import it.mithril.app.ui.theme.Ease
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T

/** Segnaposto a righe oblique, come nel prototipo. */
@Composable
fun Stripes(modifier: Modifier = Modifier) {
    val bg = M.c.surface2
    val line = M.c.ink.copy(alpha = if (M.c.dark) 0.06f else 0.05f)
    Canvas(modifier.background(bg)) {
        val step = 9.dp.toPx()
        var x = -size.height
        while (x < size.width) {
            drawLine(line, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 4.dp.toPx())
            x += step
        }
    }
}

/** Miniatura YouTube con badge numero (alto a sinistra) e durata (basso a destra). */
@Composable
fun Thumb(
    url: String?,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    radius: Dp = 12.dp,
    duration: String? = null,
    number: Int? = null,
) {
    Box(modifier.size(width, height).clip(RoundedCornerShape(radius))) {
        if (url.isNullOrBlank()) Stripes(Modifier.fillMaxSize())
        else SubcomposeAsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            loading = { Stripes(Modifier.fillMaxSize()) }, error = { Stripes(Modifier.fillMaxSize()) },
        )
        if (number != null) Box(
            Modifier.padding(5.dp).size(20.dp).clip(CircleShape).background(M.c.accent).align(Alignment.TopStart),
            contentAlignment = Alignment.Center,
        ) { Text("$number", style = ts(11, FontWeight.Bold), color = M.c.onAccent) }
        if (duration != null) Box(
            Modifier.padding(5.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xC714100E)).padding(horizontal = 5.dp, vertical = 2.dp).align(Alignment.BottomEnd),
        ) { Text(duration, style = ts(11, FontWeight.SemiBold), color = Color(0xFFF2EAE2)) }
    }
}

/** Riga dei 7 giorni nella fascia hero (pillole 44×62). */
@Composable
fun HeroDays(days: List<DayInfo>, modifier: Modifier = Modifier, key: Any? = Unit) {
    val c = M.c
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEachIndexed { i, d ->
            val shape = RoundedCornerShape(22.dp)
            val (bg, fg) = when (d.state) {
                DayState.DONE -> c.onHero to c.hero
                DayState.MINI -> c.heroSoft to c.onHero
                else -> Color.Transparent to c.onHero
            }
            Box(Modifier.dotIn(i, key), contentAlignment = Alignment.Center) {
                if (d.isToday) Box(Modifier.size(53.dp, 71.dp).border(1.5.dp, c.heroLine, RoundedCornerShape(26.5.dp)))
                Column(
                    Modifier
                        .size(44.dp, 62.dp)
                        .alpha(when (d.state) { DayState.REST -> 0.7f; DayState.SKIPPED -> 0.8f; else -> 1f })
                        .clip(shape)
                        .background(bg)
                        .then(
                            when (d.state) {
                                DayState.MINI -> Modifier.border(1.5.dp, c.heroLine, shape)
                                DayState.PLANNED -> Modifier.border(2.dp, c.onHero, shape)
                                DayState.SKIPPED -> Modifier.dashedBorder(c.heroLine, 22.dp, 1.5.dp)
                                else -> Modifier
                            }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(Fmt.dayLetter(d.date.dayOfWeek), style = ts(11, FontWeight.Medium), color = fg.copy(alpha = 0.85f))
                    Spacer(Modifier.height(4.dp))
                    if (d.state == DayState.DONE || d.state == DayState.MINI) CheckIn(i, key) { Symbol("check", 20.dp, fg) }
                    else Text("${d.date.dayOfMonth}", style = ts(17, FontWeight.SemiBold), color = fg)
                }
            }
        }
    }
}

/** Il check entra ruotando (450 ms a molla, dopo 400 ms). */
@Composable
fun CheckIn(index: Int = 0, key: Any? = Unit, content: @Composable () -> Unit) {
    val motion = M.motion
    val p = remember(key) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(key) {
        if (motion) { kotlinx.coroutines.delay(400L + 45L * index); p.animateTo(1f, androidx.compose.animation.core.spring(0.55f, 380f)) }
    }
    Box(Modifier.graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f); scaleX = 0.3f + 0.7f * v; scaleY = 0.3f + 0.7f * v; rotationZ = -20f * (1f - v)
    }) { content() }
}

/** Cerchi 40 della revisione/flusso. */
@Composable
fun ReviewDays(days: List<DayInfo>, modifier: Modifier = Modifier, key: Any? = Unit) {
    val c = M.c
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEachIndexed { i, d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.dotIn(i, key)) {
                Text(Fmt.dayLetter(d.date.dayOfWeek), style = ts(11, FontWeight.Medium), color = c.ink3)
                Spacer(Modifier.height(6.dp))
                val mod = Modifier.size(38.dp).clip(CircleShape)
                when (d.state) {
                    DayState.DONE -> Box(mod.background(c.accent), contentAlignment = Alignment.Center) { CheckIn(i, key) { Symbol("check", 20.dp, c.onAccent) } }
                    DayState.MINI -> Box(mod.background(c.accentSoft), contentAlignment = Alignment.Center) { CheckIn(i, key) { Symbol("check", 20.dp, c.accentInk) } }
                    DayState.PLANNED -> Box(Modifier.size(38.dp).border(2.dp, c.accent, CircleShape).then(if (d.isToday) Modifier.background(c.surface, CircleShape) else Modifier))
                    DayState.SKIPPED -> Box(Modifier.size(38.dp).dashedBorder(c.line2, 19.dp, 1.5.dp))
                    DayState.REST -> Box(mod.background(c.surface2))
                }
            }
        }
    }
}

/** Puntini della settimana nella schermata di completamento. */
@Composable
fun CompletionDots(days: List<DayInfo>, modifier: Modifier = Modifier) {
    val c = M.c
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        days.forEachIndexed { i, d ->
            Box(Modifier.size(12.dp).dotIn(i), contentAlignment = Alignment.Center) {
                when (d.state) {
                    DayState.DONE -> Box(Modifier.size(12.dp).background(c.onHero, CircleShape))
                    DayState.MINI -> Box(Modifier.size(12.dp).border(2.dp, c.onHero, CircleShape))
                    DayState.PLANNED -> Box(Modifier.size(12.dp).border(1.5.dp, c.heroLine, CircleShape))
                    DayState.SKIPPED -> Box(Modifier.size(6.dp).background(c.heroLine, CircleShape))
                    DayState.REST -> Box(Modifier.size(4.dp).background(c.heroLine, CircleShape))
                }
            }
        }
    }
}

/** Linea che si "disegna" (trim 0→1, 1,2 s, ritardo 200 ms). */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = M.c.accent, stroke: Dp = 2.2.dp) {
    val motion = M.motion
    val draw = remember(values.size) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(values.size) { if (motion) draw.animateTo(1f, tween(1200, 200, Ease.Draw)) }
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val lo = values.min(); val hi = values.max(); val span = (hi - lo).coerceAtLeast(0.4)
        val pad = stroke.toPx()
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = pad + (size.width - 2 * pad) * i / (values.size - 1)
            val y = pad + (size.height - 2 * pad) * (1 - ((v - lo) / span)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawTrimmed(path, draw.value, color, stroke.toPx())
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTrimmed(path: Path, fraction: Float, color: Color, width: Float, alpha: Float = 1f) {
    if (fraction <= 0f) return
    val out = if (fraction >= 1f) path else Path().also { dst ->
        val pm = PathMeasure(); pm.setPath(path, false)
        pm.getSegment(0f, pm.length * fraction, dst, true)
    }
    drawPath(out, color, alpha = alpha, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Segmento di avanzamento del flusso a 4 passi. */
@Composable
fun StepDashes(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val col = androidx.compose.animation.animateColorAsState(if (i <= current) M.c.accent else M.c.surface2, tween(450), label = "dash").value
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(col))
        }
    }
}

@Composable
fun NumberBadge(n: Int, size: Dp = 18.dp, bg: Color = M.c.accentSoft, fg: Color = M.c.accentInk) {
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text("$n", style = ts((size.value * 0.58f).toInt().coerceAtLeast(10), FontWeight.Bold).copy(fontSize = (size.value * 0.58f).sp), color = fg)
    }
}

@Composable
fun StatValue(value: String, unit: String?, style: androidx.compose.ui.text.TextStyle = T.trendBig, color: Color = M.c.ink) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = style, color = color)
        if (unit != null) Text(unit, style = T.body14, color = M.c.ink2, modifier = Modifier.padding(start = 3.dp, bottom = (style.fontSize.value * 0.12f).dp))
    }
}
