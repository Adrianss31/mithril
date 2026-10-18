package it.mithril.app.ui.session

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

/**
 * Mostra [view] come vista nativa sopra Compose, allineata al riquadro di questo composable.
 * La WebView del player sta fuori dai layer di Compose: dentro (animazioni, layer fuori schermo)
 * Chromium non riesce a comporre il video e si vede nero con l'audio che va.
 */
@Composable
fun NativeHost(view: View, visible: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val root = context.findActivity()?.findViewById<FrameLayout>(android.R.id.content)
    DisposableEffect(view, root) {
        if (root != null && view.parent == null) root.addView(view, FrameLayout.LayoutParams(0, 0))
        onDispose { (view.parent as? FrameLayout)?.removeView(view) }
    }
    view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    Box(modifier.onGloballyPositioned { c ->
        val r = root ?: return@onGloballyPositioned
        val origin = IntArray(2).also { r.getLocationInWindow(it) }
        val pos = c.positionInWindow()
        val lp = (view.layoutParams as? FrameLayout.LayoutParams) ?: FrameLayout.LayoutParams(0, 0)
        val w = c.size.width; val h = c.size.height
        val x = pos.x.roundToInt() - origin[0]; val y = pos.y.roundToInt() - origin[1]
        if (lp.width != w || lp.height != h || lp.leftMargin != x || lp.topMargin != y) {
            lp.width = w; lp.height = h; lp.leftMargin = x; lp.topMargin = y
            view.layoutParams = lp
        }
    })
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
