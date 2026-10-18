package it.mithril.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.mithril.app.ui.theme.ts
import it.mithril.app.ui.components.LocalToast
import it.mithril.app.ui.components.Sheet
import it.mithril.app.ui.components.Symbol
import it.mithril.app.ui.components.ToastHost
import it.mithril.app.ui.components.ToastState
import it.mithril.app.ui.library.AddLinkSheet
import it.mithril.app.ui.library.AddVideoSheet
import it.mithril.app.ui.library.LibraryScreen
import it.mithril.app.ui.planner.NewSlotSheet
import it.mithril.app.ui.planner.PlanWeekScreen
import it.mithril.app.ui.planner.PlannerScreen
import it.mithril.app.ui.review.ReviewScreen
import it.mithril.app.ui.session.CastSheet
import it.mithril.app.ui.session.CompletionScreen
import it.mithril.app.ui.session.ExternalVideoScreen
import it.mithril.app.ui.session.PlayerScreen
import it.mithril.app.ui.settings.SettingsScreen
import it.mithril.app.ui.theme.Ease
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.today.TodayScreen
import it.mithril.app.ui.weight.AllWeightsScreen
import it.mithril.app.ui.weight.WeighSheet
import it.mithril.app.ui.weight.WeightScreen

val LocalNav = staticCompositionLocalOf { Navigator() }

@Composable
fun AppRoot(nav: Navigator, toast: ToastState = remember { ToastState() }) {
    CompositionLocalProvider(LocalNav provides nav, LocalToast provides toast) {
        Box(Modifier.fillMaxSize().background(M.c.bg)) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { TabsContent(nav) }
                BottomNav(nav)
            }
            Overlays(nav)
            val sheet = nav.sheet
            Sheet(sheet, onDismiss = { nav.sheet = null }) { s ->
                when (s) {
                    is SheetKind.AddVideo -> AddVideoSheet(s, onDone = { nav.sheet = null })
                    is SheetKind.NewSlot -> NewSlotSheet(s, onDone = { nav.sheet = null })
                    is SheetKind.Weigh -> WeighSheet(s, onDone = { nav.sheet = null })
                    SheetKind.AddLink -> AddLinkSheet(onDone = { nav.sheet = it })
                    SheetKind.CastPicker -> CastSheet(onDone = { nav.sheet = null })
                }
            }
            ToastHost(toast, bottom = if (nav.stack.isEmpty()) 104.dp else 32.dp)
        }
    }
}

@Composable
private fun TabsContent(nav: Navigator) {
    val holder = rememberSaveableStateHolder()
    val motion = M.motion
    AnimatedContent(
        targetState = nav.tab,
        transitionSpec = {
            if (!motion) EnterTransition.None togetherWith ExitTransition.None
            else (fadeIn(tween(450, easing = Ease.Standard)) + slideInVertically(tween(450, easing = Ease.Standard)) { 30 } +
                androidx.compose.animation.scaleIn(tween(450, easing = Ease.Standard), initialScale = 0.985f)) togetherWith fadeOut(tween(150))
        },
        label = "tabs",
    ) { tab ->
        holder.SaveableStateProvider(tab.name) {
            when (tab) {
                Tab.TODAY -> TodayScreen()
                Tab.PLANNER -> PlannerScreen()
                Tab.LIBRARY -> LibraryScreen()
                Tab.WEIGHT -> WeightScreen()
            }
        }
    }
}

@Composable
private fun BottomNav(nav: Navigator) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(M.c.nav)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 12.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { t ->
            val active = nav.tab == t
            val pillW by animateDpAsState(if (active) 64.dp else 36.dp, if (M.motion) spring(0.6f, 400f) else tween(0), label = "pill")
            val pillBg by animateColorAsState(if (active) M.c.accentSoft else M.c.accentSoft.copy(alpha = 0f), tween(300), label = "pillBg")
            val scale by animateFloatAsState(if (active && M.motion) 1.06f else 1f, spring(0.6f, 400f), label = "icon")
            Column(
                Modifier
                    .weight(1f)
                    .clickable(remember { MutableInteractionSource() }, null) { nav.select(t) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(64.dp, 32.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(pillW).height(32.dp).clip(RoundedCornerShape(16.dp)).background(pillBg))
                    Symbol(t.icon, 24.dp, if (active) M.c.ink else M.c.ink2, filled = active, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
                }
                Spacer(Modifier.height(4.dp))
                Text(t.label, style = ts(12, if (active) FontWeight.Bold else FontWeight.Medium), color = if (active) M.c.ink else M.c.ink2)
            }
        }
    }
}

@Composable
private fun Overlays(nav: Navigator) {
    val top = nav.top
    BackHandler(enabled = top != null && nav.sheet == null) { nav.pop() }
    val motion = M.motion
    AnimatedContent(
        targetState = top,
        transitionSpec = { overlayTransition(initialState, targetState, motion) },
        contentKey = { it?.let { d -> d::class.simpleName + (d as? Dest.Player)?.req.hashCode() } },
        label = "overlay",
    ) { d ->
        if (d == null) Box(Modifier) else Box(Modifier.fillMaxSize()) {
            when (d) {
                is Dest.Player -> PlayerScreen(d.req)
                is Dest.External -> ExternalVideoScreen(d)
                is Dest.Completion -> CompletionScreen(d)
                is Dest.Review -> ReviewScreen(d.weekStart)
                is Dest.PlanWeek -> PlanWeekScreen(d.weekStart)
                Dest.Settings -> SettingsScreen()
                Dest.AllWeights -> AllWeightsScreen()
            }
        }
    }
}

private fun overlayTransition(from: Dest?, to: Dest?, motion: Boolean): ContentTransform {
    if (!motion) return EnterTransition.None togetherWith ExitTransition.None
    val enter = when (to) {
        null -> EnterTransition.None
        is Dest.Player -> fadeIn(tween(450, easing = Ease.LongOut)) + slideInVertically(tween(450, easing = Ease.LongOut)) { (it * 0.06f).toInt() }
        // Il completamento ha la sua rivelazione circolare.
        is Dest.Completion -> EnterTransition.None
        else -> fadeIn(tween(420, easing = Ease.Standard)) + slideInHorizontally(tween(420, easing = Ease.Standard)) { (it * 0.08f).toInt() }
    }
    val exit = if (to is Dest.Completion) fadeOut(tween(700, 300)) else fadeOut(tween(220))
    return enter togetherWith exit
}

/** Spazio sotto il contenuto delle tab, per non finire sotto FAB e toast. */
val BottomGap = 24.dp
