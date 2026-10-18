package it.mithril.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mithril.app.ui.theme.pressScale
import it.mithril.app.R
import it.mithril.app.ui.theme.Ease
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T

private val SymbolsOutline = FontFamily(Font(R.font.symbols_fill0))
private val SymbolsFilled = FontFamily(Font(R.font.symbols_fill1))

/** Icona Material Symbols Rounded (ligature); [filled] = FILL 1. */
@Composable
fun Symbol(name: String, size: Dp = 24.dp, color: Color = M.c.ink, filled: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        name,
        modifier = modifier,
        style = TextStyle(
            fontFamily = if (filled) SymbolsFilled else SymbolsOutline,
            fontSize = (size.value).sp, lineHeight = (size.value).sp, color = color,
            fontFeatureSettings = "liga",
        ),
        maxLines = 1,
        softWrap = false,
    )
}

fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp, dash: Float = 6f, gap: Float = 5f): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash * density / 2, gap * density / 2))),
    )
}

/** Ombra calda delle card (solo tema chiaro). */
@Composable
fun Modifier.cardShadow(shape: Shape): Modifier =
    if (M.c.dark) this else this.shadow(6.dp, shape, clip = false, ambientColor = Color(0x223C281E), spotColor = Color(0x283C281E))

@Composable
fun Card(
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    padding: PaddingValues = PaddingValues(20.dp),
    color: Color = M.c.surface,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val source = remember { MutableInteractionSource() }
    Column(
        modifier
            .then(if (onClick != null) Modifier.pressScale(source, 0.985f) else Modifier)
            .cardShadow(shape)
            .clip(shape)
            .background(color)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(source, null, role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
private fun BaseButton(
    onClick: () -> Unit,
    modifier: Modifier,
    height: Dp,
    bg: Color,
    fg: Color,
    border: BorderStroke? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp),
    content: @Composable RowScope.(Color) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(height / 2)
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, tween(200), label = "enabled")
    Row(
        modifier
            .pressScale(source)
            .alpha(alpha)
            .height(height)
            .clip(shape)
            .background(bg)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(source, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) { content(fg) }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    iconFilled: Boolean = true,
    height: Dp = 54.dp,
    enabled: Boolean = true,
    bg: Color = M.c.accent,
    fg: Color = M.c.onAccent,
) = BaseButton(onClick, modifier, height, bg, fg, enabled = enabled) { c ->
    if (icon != null) Symbol(icon, if (height >= 54.dp) 22.dp else 20.dp, c, iconFilled)
    Text(text, style = if (height >= 54.dp) T.button else T.button15, color = c, maxLines = 1)
}

@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    height: Dp = 54.dp,
    enabled: Boolean = true,
) = BaseButton(onClick, modifier, height, M.c.accentSoft, M.c.accentInk, enabled = enabled,
    contentPadding = PaddingValues(horizontal = if (height <= 40.dp) 16.dp else 20.dp)) { c ->
    if (icon != null) Symbol(icon, 20.dp, c)
    Text(text, style = if (height <= 40.dp) T.button15.copy(fontSize = 14.sp) else T.button15, color = c, maxLines = 1)
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    height: Dp = 52.dp,
    color: Color = M.c.ink,
    borderColor: Color = M.c.line2,
    enabled: Boolean = true,
) = BaseButton(onClick, modifier, height, Color.Transparent, color, BorderStroke(1.dp, borderColor), enabled) { c ->
    if (icon != null) Symbol(icon, 20.dp, c)
    Text(text, style = T.button15, color = c, maxLines = 1)
}

@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = M.c.accent) {
    Text(
        text,
        style = T.button15.copy(fontSize = 14.sp),
        color = color,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
    )
}

/** Chip selezionabile (h 40, raggio 20). */
@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCheck: Boolean = false,
    height: Dp = 40.dp,
    onColor: Color = M.c.accentSoft,
    onInk: Color = M.c.accentInk,
    offInk: Color = M.c.ink,
    offBorder: Color = M.c.line2,
    dashed: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) onColor else Color.Transparent, tween(250), label = "chipBg")
    val fg by animateColorAsState(if (selected) onInk else offInk, tween(250), label = "chipFg")
    val scale by animateFloatAsState(if (selected && M.motion) 1.02f else 1f, tween(250), label = "chipScale")
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pressScale(source)
            .height(height)
            .clip(shape)
            .background(bg)
            .then(
                when {
                    selected -> Modifier
                    dashed -> Modifier.dashedBorder(offBorder, height / 2, 1.dp)
                    else -> Modifier.border(1.dp, offBorder, shape)
                }
            )
            .clickable(source, null, role = Role.Checkbox, onClick = onClick)
            .padding(start = if (selected && showCheck) 10.dp else 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected && showCheck) Symbol("check", 18.dp, fg)
        Text(label, style = T.button15.copy(fontSize = 14.sp), color = fg, maxLines = 1)
    }
}

/** Piccolo chip informativo (h 26, raggio 13). */
@Composable
fun SmallChip(label: String, bg: Color = M.c.accentSoft, fg: Color = M.c.accentInk, border: Color? = null, dashed: Boolean = false) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        Modifier
            .height(26.dp)
            .clip(shape)
            .background(bg)
            .then(if (border != null) (if (dashed) Modifier.dashedBorder(border, 13.dp, 1.dp) else Modifier.border(1.dp, border, shape)) else Modifier)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = T.captionBold, color = fg, maxLines = 1) }
}

/** Etichetta di un video (11/600, surface2, raggio 8). */
@Composable
fun Tag(label: String) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(M.c.surface2).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(label, style = T.overline.copy(letterSpacing = 0.sp, fontSize = 11.sp), color = M.c.ink2)
    }
}

/** Pulsante tondo con icona (stepper, chiudi, +). */
@Composable
fun RoundIconButton(
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    bg: Color = M.c.surface2,
    fg: Color = M.c.ink,
    enabled: Boolean = true,
    filled: Boolean = false,
    contentDescription: String? = null,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(source, 0.92f)
            .size(size)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(bg)
            .clickable(source, null, enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Symbol(icon, iconSize, fg, filled) }
}

@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = M.c.ink2) =
    Text(text.uppercase(), style = T.overline, color = color, modifier = modifier)

/** Barra orizzontale (passi, obiettivo) che cresce da sinistra. */
@Composable
fun GrowBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 10.dp, track: Color = M.c.surface2, fill: Color = M.c.accent, delayMs: Int = 350) {
    val motion = M.motion
    val grow = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) { if (motion) grow.animateTo(1f, tween(1000, delayMs, Ease.Standard)) }
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "bar")
    Box(modifier.height(height).clip(RoundedCornerShape(height / 2)).background(track)) {
        Box(
            Modifier
                .matchParentSizeFraction(f * grow.value)
                .clip(RoundedCornerShape(height / 2))
                .background(fill)
        )
    }
}

private fun Modifier.matchParentSizeFraction(f: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val w = (constraints.maxWidth * f).toInt().coerceAtLeast(0)
        val p = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
        layout(constraints.maxWidth, constraints.maxHeight) { p.place(0, 0) }
    }
)

@Composable
fun Hint(text: String, modifier: Modifier = Modifier, icon: String? = null, bg: Color = M.c.surface2, fg: Color = M.c.ink2) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) Symbol(icon, 20.dp, fg)
        Text(text, style = T.body14, color = fg)
    }
}

@Composable
fun EmptySpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun W(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun Title(text: String, sub: String? = null, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, style = T.screenTitle, color = M.c.ink)
            if (sub != null) Text(sub, style = T.secondary, color = M.c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
fun CenterText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) =
    Text(text, style = style, color = color, textAlign = TextAlign.Center, modifier = modifier)

@Composable
fun Divider(modifier: Modifier = Modifier, color: Color = M.c.line) = Box(modifier.height(1.dp).background(color).heightIn(1.dp))
