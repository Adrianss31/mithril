package it.mithril.app.ui.weight

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mithril.app.ui.rememberRepo
import it.mithril.app.ui.theme.ts
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import it.mithril.app.domain.Fmt
import it.mithril.app.ui.SheetKind
import it.mithril.app.ui.components.Divider
import it.mithril.app.ui.components.LocalToast
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.components.RoundIconButton
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T

/** Foglio "Nuova pesata / Modifica pesata" con rullo a decimi di kg. */
@Composable
fun ColumnScope.WeighSheet(s: SheetKind.Weigh, onDone: () -> Unit) {
    val repo = rememberRepo()
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val weights by repo.weights.collectAsStateWithLifecycle()
    val list = weights ?: emptyList()
    val date = s.editDate ?: LocalDate.now()
    val existing = list.firstOrNull { it.date == date.toEpochDay() }
    val last = list.lastOrNull()
    var tenths by remember(s) { mutableIntStateOf(((existing ?: last)?.kg ?: 75.0).times(10).let { Math.round(it).toInt() }) }
    var initialized by remember(s) { mutableStateOf(weights != null) }
    LaunchedEffect(weights) {
        if (!initialized && weights != null) { tenths = Math.round(((existing ?: last)?.kg ?: 75.0) * 10).toInt(); initialized = true }
    }
    val now = LocalTime.now()
    Text(if (s.editDate != null) "Modifica pesata" else "Nuova pesata", style = T.sheetTitle, color = M.c.ink)
    Text(
        when {
            s.editDate != null -> Fmt.dayShort(date).replaceFirstChar { it.uppercase() }
            last == null -> "Al mattino, dopo il bagno, è il momento ideale"
            else -> "Oggi, ${"%02d:%02d".format(now.hour, now.minute)} · ultima ${Fmt.kg(last.kg)} kg"
        },
        style = T.secondary, color = M.c.ink2,
    )
    Spacer(Modifier.height(18.dp))
    WeightWheel(tenths, { tenths = it.coerceIn(200, 3000) }, stepper = 56.dp, big = true)
    Spacer(Modifier.height(22.dp))
    PrimaryButton("Salva", {
        val kg = tenths / 10.0
        scope.launch {
            repo.saveWeight(date, kg)
            toast.show("Pesata salvata · ${Fmt.kg(kg)} kg")
        }
        onDone()
    }, Modifier.fillMaxWidth(), height = 56.dp)
}

/** Rullo: valore centrale tra due linee, ±0,1 sopra e sotto sfumati; trascinamento verticale e stepper ai lati. */
@Composable
fun WeightWheel(tenths: Int, onChange: (Int) -> Unit, stepper: Dp = 56.dp, big: Boolean = true) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var acc by remember { mutableFloatStateOf(0f) }
    val stepPx = with(density) { 18.dp.toPx() }
    fun set(v: Int) { if (v != tenths) { onChange(v); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton("remove", { set(tenths - 1) }, size = stepper, iconSize = 26.dp, contentDescription = "−0,1 kg")
        Column(
            Modifier.weight(1f).draggable(
                rememberDraggableState { d ->
                    acc += d
                    while (acc > stepPx) { acc -= stepPx; set(tenths - 1) }
                    while (acc < -stepPx) { acc += stepPx; set(tenths + 1) }
                },
                Orientation.Vertical,
                onDragStopped = { acc = 0f },
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (big) {
                Text(Fmt.kg((tenths + 2) / 10.0), style = ts(20, FontWeight.Medium), color = M.c.ink3.copy(alpha = 0.5f))
                Text(Fmt.kg((tenths + 1) / 10.0), style = ts(24, FontWeight.Medium), color = M.c.ink3)
                Spacer(Modifier.height(6.dp))
                Divider(Modifier.fillMaxWidth(0.8f), M.c.line2)
            }
            Row(Modifier.padding(vertical = if (big) 4.dp else 0.dp), verticalAlignment = Alignment.Bottom) {
                AnimatedContent(
                    tenths,
                    transitionSpec = {
                        val up = targetState > initialState
                        (fadeIn(tween(160)) + slideInVertically(tween(160)) { if (up) it / 3 else -it / 3 }) togetherWith
                            (fadeOut(tween(120)) + slideOutVertically(tween(120)) { if (up) -it / 3 else it / 3 })
                    },
                    label = "wheel",
                ) { v ->
                    Text(Fmt.kg(v / 10.0), style = if (big) T.weighValue else ts(36, FontWeight.SemiBold), color = M.c.ink, textAlign = TextAlign.Center)
                }
                Text("kg", style = T.body14, color = M.c.ink2, modifier = Modifier.padding(start = 4.dp, bottom = if (big) 10.dp else 6.dp))
            }
            if (big) {
                Divider(Modifier.fillMaxWidth(0.8f), M.c.line2)
                Spacer(Modifier.height(6.dp))
                Text(Fmt.kg((tenths - 1) / 10.0), style = ts(24, FontWeight.Medium), color = M.c.ink3)
                Text(Fmt.kg((tenths - 2) / 10.0), style = ts(20, FontWeight.Medium), color = M.c.ink3.copy(alpha = 0.5f))
            }
        }
        RoundIconButton("add", { set(tenths + 1) }, size = stepper, iconSize = 26.dp, contentDescription = "+0,1 kg")
    }
    Box(Modifier.width(0.dp))
}
