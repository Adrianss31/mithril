package it.mithril.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import it.mithril.app.ui.theme.Ease
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T

/**
 * Bottom sheet del prototipo: scrim in dissolvenza (300 ms), foglio che sale (500 ms, uscita lunga),
 * maniglia trascinabile, altezza massima 90%. Mantiene l'ultimo [item] durante l'animazione di uscita.
 */
@Composable
fun <T : Any> Sheet(item: T?, onDismiss: () -> Unit, maxFraction: Float = 0.9f, content: @Composable ColumnScope.(T) -> Unit) {
    var last by remember { mutableStateOf(item) }
    if (item != null) last = item
    val state = remember { MutableTransitionState(false) }
    state.targetState = item != null
    val motion = M.motion
    if (!state.currentState && !state.targetState && state.isIdle) { last = null; return }
    val shown = last ?: return
    BackHandler(enabled = item != null, onBack = onDismiss)
    val density = LocalDensity.current
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(item != null) { if (item != null) drag.snapTo(0f) }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(state, enter = fadeIn(tween(if (motion) 300 else 0)), exit = fadeOut(tween(if (motion) 250 else 0))) {
            Box(Modifier.fillMaxSize().background(M.c.scrim).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
        }
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            val maxH = maxHeight * maxFraction
            AnimatedVisibility(
                state,
                enter = slideInVertically(tween(if (motion) 500 else 0, easing = Ease.LongOut)) { it },
                exit = slideOutVertically(tween(if (motion) 300 else 0, easing = Ease.Standard)) { it },
            ) {
                Column(
                    Modifier
                        .offset { IntOffset(0, drag.value.roundToInt()) }
                        .fillMaxWidth()
                        .heightIn(max = maxH)
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(M.c.surface)
                        .clickable(remember { MutableInteractionSource() }, null) {}
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .draggable(
                                rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } },
                                Orientation.Vertical,
                                onDragStopped = { v ->
                                    if (drag.value > with(density) { 110.dp.toPx() } || v > 2200f) onDismiss()
                                    else scope.launch { drag.animateTo(0f, spring(0.8f, 500f)) }
                                },
                            )
                            .padding(top = 12.dp, bottom = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(32.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(M.c.ink3.copy(alpha = 0.5f)))
                    }
                    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 30.dp)) { content(shown) }
                }
            }
        }
    }
}

@Stable
class ToastState {
    data class Toast(val text: String, val action: String?, val onAction: (() -> Unit)?, val id: Long = System.nanoTime(), val durationMs: Long = 3200)
    var current by mutableStateOf<Toast?>(null)
        private set
    fun show(text: String, action: String? = null, durationMs: Long = 3200, onAction: (() -> Unit)? = null) {
        current = Toast(text, action, onAction, durationMs = durationMs)
    }
    fun dismiss(id: Long) { if (current?.id == id) current = null }
}

val LocalToast = staticCompositionLocalOf { ToastState() }

/** Toast centrato in basso: sale di 18 px con scala .94→1 a molla, sparisce dopo 3,2 s. */
@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier, bottom: androidx.compose.ui.unit.Dp = 104.dp) {
    val toast = state.current
    var last by remember { mutableStateOf(toast) }
    if (toast != null) last = toast
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = toast != null
    LaunchedEffect(toast?.id) {
        val t = toast ?: return@LaunchedEffect
        delay(t.durationMs); state.dismiss(t.id)
    }
    val motion = M.motion
    Box(modifier.fillMaxSize().padding(bottom = bottom), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(if (motion) 250 else 0)) + slideInVertically(if (motion) spring(0.6f, 400f) else tween(0)) { 18 } +
                scaleIn(if (motion) spring(0.6f, 400f) else tween(0), initialScale = 0.94f),
            exit = fadeOut(tween(if (motion) 200 else 0)) + scaleOut(tween(if (motion) 200 else 0), targetScale = 0.96f),
        ) {
            val t = last ?: return@AnimatedVisibility
            Row(
                Modifier
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(M.c.ink)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(t.text, style = T.body14.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = M.c.surface)
                if (t.action != null) {
                    Text(
                        t.action,
                        style = T.button15.copy(fontSize = 14.sp),
                        color = if (M.c.dark) androidx.compose.ui.graphics.Color(0xFF9A4A30) else M.c.accentSoft,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { t.onAction?.invoke(); state.dismiss(t.id) }.padding(4.dp),
                    )
                }
            }
        }
    }
}
