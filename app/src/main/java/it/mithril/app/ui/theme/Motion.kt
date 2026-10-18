package it.mithril.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.animateFloatAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Curve e tempi del prototipo. */
object Ease {
    val Standard = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    val LongOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val Draw = CubicBezierEasing(0.6f, 0f, 0.2f, 1f)
    val Reveal = CubicBezierEasing(0.7f, 0f, 0.2f, 1f)
}

fun <T> springPop(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 400f)
fun <T> std(ms: Int, delay: Int = 0): FiniteAnimationSpec<T> = tween(ms, delay, Ease.Standard)

/** Restituisce la specifica o uno "snap" se le animazioni di sistema sono disattivate. */
@Composable
fun <T> anim(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> = if (M.motion) spec else snap()

@Suppress("UNUSED_PARAMETER")
fun <T> AnimationSpec<T>.orSnap(motion: Boolean): AnimationSpec<T> = if (motion) this else snap()

/**
 * Ingresso "in cascata": dissolvenza + salita di [dy] dp, durata [ms], ritardo 60 ms × indice (max 500 ms).
 * Rigiocata ogni volta che cambia [key].
 */
fun Modifier.rise(index: Int = 0, key: Any? = Unit, dy: Float = 14f, ms: Int = 550, stagger: Int = 60, baseDelay: Int = 0): Modifier = composed {
    val motion = M.motion
    val density = LocalDensity.current.density
    val p = remember(key) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(key) {
        if (motion) {
            delay((baseDelay + index * stagger).coerceAtMost(500).toLong())
            p.animateTo(1f, tween(ms, easing = Ease.Standard))
        }
    }
    graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * dy * density
    }
}

/** Pop dei giorni della settimana: sale di 8 dp e scala da 0,6 a molla. */
fun Modifier.dotIn(index: Int, key: Any? = Unit): Modifier = composed {
    val motion = M.motion
    val density = LocalDensity.current.density
    val a = remember(key) { Animatable(if (motion) 0f else 1f) }
    val s = remember(key) { Animatable(if (motion) 0.6f else 1f) }
    LaunchedEffect(key) {
        if (motion) {
            delay(80L + 45L * index)
            launch { a.animateTo(1f, tween(250)) }
            s.animateTo(1f, spring(0.6f, 400f))
        }
    }
    graphicsLayer {
        alpha = a.value
        scaleX = s.value; scaleY = s.value
        translationY = (1f - a.value) * 8f * density
    }
}

/** Pressione dei pulsanti: scala a 0,95 in 80 ms, ritorno a molla. */
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.95f): Modifier = composed {
    val isPressed by source.collectIsPressedAsState()
    val motion = M.motion
    val scale by animateFloatAsState(
        if (isPressed && motion) pressed else 1f,
        if (isPressed) tween(80) else spring(0.6f, 500f), label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}
