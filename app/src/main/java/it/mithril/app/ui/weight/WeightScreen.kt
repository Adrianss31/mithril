package it.mithril.app.ui.weight

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mithril.app.ui.rememberRepo
import it.mithril.app.ui.statusBarTop
import it.mithril.app.ui.theme.rise
import it.mithril.app.ui.theme.ts
import kotlinx.coroutines.launch
import java.time.LocalDate
import it.mithril.app.data.WeightEntity
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Goal
import it.mithril.app.domain.Trend
import it.mithril.app.ui.BottomGap
import it.mithril.app.ui.Dest
import it.mithril.app.ui.LocalNav
import it.mithril.app.ui.SheetKind
import it.mithril.app.ui.components.Card
import it.mithril.app.ui.components.Divider
import it.mithril.app.ui.components.LocalToast
import it.mithril.app.ui.components.Overline
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.components.RoundIconButton
import it.mithril.app.ui.components.StatValue
import it.mithril.app.ui.components.Symbol
import it.mithril.app.ui.components.TextLink
import it.mithril.app.ui.components.TonalButton
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T

@Composable
fun WeightScreen() {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val today by repo.today.collectAsStateWithLifecycle()
    val weightsOrNull by repo.weights.collectAsStateWithLifecycle()
    val settings by repo.appSettings.collectAsStateWithLifecycle()
    val weights = weightsOrNull ?: emptyList()
    val pairs = remember(weights) { weights.map { it.date to it.kg } }
    val trend = remember(pairs, today) { Trend.of(pairs, today.toEpochDay()) }
    var range by rememberSaveable { mutableStateOf(Range.M3) }
    val enough = weights.size >= Trend.MIN_WEIGH_INS
    val epoch = nav.tabEpoch

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = statusBarTop() + 14.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Peso", style = T.screenTitle, color = M.c.ink, modifier = Modifier.weight(1f))
            RoundIconButton("settings", { nav.push(Dest.Settings) }, size = 40.dp, iconSize = 22.dp, bg = M.c.bg, fg = M.c.ink2, contentDescription = "Impostazioni")
            Spacer(Modifier.width(6.dp))
            TonalButton("Pesata", { nav.sheet = SheetKind.Weigh() }, icon = "add", height = 40.dp)
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                weightsOrNull == null -> {}
                weights.isEmpty() -> Card(Modifier.rise(0, epoch).fillMaxWidth()) {
                    Text("Qui conta la tendenza", style = T.heroTitle, color = M.c.ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Il peso di un singolo giorno oscilla per acqua, sale e sonno. Mithril calcola una media mobile delle pesate: " +
                            "la linea del trend si muove piano e mostra dove stai andando davvero. Pesati quando vuoi, meglio al mattino.",
                        style = T.body, color = M.c.ink2,
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Fai la prima pesata", { nav.sheet = SheetKind.Weigh() }, Modifier.fillMaxWidth(), icon = "add", height = 48.dp)
                }
                !enough -> Card(Modifier.rise(0, epoch).fillMaxWidth()) {
                    Text("Ultima pesata", style = T.secondary, color = M.c.ink2)
                    StatValue(Fmt.kg(weights.last().kg), "kg")
                    Spacer(Modifier.height(8.dp))
                    val n = Trend.MIN_WEIGH_INS - weights.size
                    Text("Ancora $n ${if (n == 1) "pesata" else "pesate"} e vedrai il trend. Nessuna fretta.", style = T.body, color = M.c.ink2)
                }
                else -> TrendCard(trend, today, range, settings.goalKg, Modifier.rise(0, epoch)) { range = it }
            }
            if (enough) {
                val t = today.toEpochDay()
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChangeCard("7 giorni", trend.change(t, 7), Modifier.weight(1f).fillMaxHeight().rise(1, epoch))
                    ChangeCard("30 giorni", trend.change(t, 30), Modifier.weight(1f).fillMaxHeight().rise(2, epoch))
                }
                GoalCard(trend, today, settings.goalKg, settings.weeklyRateKg, settings.goalStartKg, Modifier.rise(3, epoch))
            } else if (weights.isNotEmpty() && settings.goalKg == null) {
                GoalCard(trend, today, null, settings.weeklyRateKg, null, Modifier.rise(1, epoch))
            }
            if (weights.isNotEmpty()) WeighInsCard(weights.reversed().take(10), weights.size, Modifier.rise(4, epoch))
            Spacer(Modifier.height(BottomGap))
        }
    }
}

@Composable
private fun TrendCard(trend: Trend, today: LocalDate, range: Range, goalKg: Double?, modifier: Modifier, onRange: (Range) -> Unit) {
    val t = today.toEpochDay()
    val now = trend.at(t)
    val change = trend.change(t, 7)
    Card(modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Trend attuale", style = T.secondary, color = M.c.ink2)
                if (now != null) StatValue(Fmt.kg(now), "kg")
            }
            if (change != null) Text("${Fmt.signedKg(change)} kg\nin 7 giorni", style = T.secondary, color = M.c.ink2, textAlign = TextAlign.End, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(14.dp))
        Segmented(range, onRange)
        Spacer(Modifier.height(16.dp))
        WeightChart(trend, today, range, goalKg, trend.dailySlope(t), Modifier.fillMaxWidth().height(190.dp))
        Spacer(Modifier.height(12.dp))
        Legend()
    }
}

@Composable
private fun Segmented(range: Range, onRange: (Range) -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp)).background(M.c.surface2).padding(3.dp)) {
        Range.entries.forEach { r ->
            val sel = r == range
            val bg by animateColorAsState(if (sel) M.c.surface else M.c.surface2, tween(250), label = "seg")
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .then(if (sel && !M.c.dark) Modifier.shadow(2.dp, RoundedCornerShape(17.dp), ambientColor = Color(0x223C281E), spotColor = Color(0x223C281E)) else Modifier)
                    .clip(RoundedCornerShape(17.dp)).background(bg).clickable { onRange(r) },
                contentAlignment = Alignment.Center,
            ) { Text(r.label, style = ts(13, if (sel) FontWeight.SemiBold else FontWeight.Medium), color = if (sel) M.c.ink else M.c.ink2) }
        }
    }
}

@Composable
private fun Legend() {
    val c = M.c
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(14.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.accent))
            Spacer(Modifier.width(5.dp)); Text("trend", style = T.caption, color = M.c.ink2)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.ink3.copy(alpha = 0.6f)))
            Spacer(Modifier.width(5.dp)); Text("pesate", style = T.caption, color = M.c.ink2)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.width(16.dp).height(3.dp)) {
                drawLine(c.accent.copy(alpha = 0.7f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            }
            Spacer(Modifier.width(5.dp)); Text("proiezione", style = T.caption, color = M.c.ink2)
        }
    }
}

@Composable
private fun ChangeCard(label: String, change: Double?, modifier: Modifier) {
    Card(modifier, radius = 22.dp, padding = PaddingValues(16.dp)) {
        Text(label, style = T.secondary, color = M.c.ink2)
        Spacer(Modifier.height(2.dp))
        Text(change?.let { "${Fmt.signedKg(it)} kg" } ?: "—", style = ts(22, FontWeight.SemiBold), color = M.c.ink)
    }
}

@Composable
private fun GoalCard(trend: Trend, today: LocalDate, goalKg: Double?, rate: Double, startKg: Double?, modifier: Modifier) {
    val nav = LocalNav.current
    Card(modifier.fillMaxWidth(), radius = 22.dp, padding = PaddingValues(16.dp), onClick = { nav.push(Dest.Settings) }) {
        if (goalKg == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Obiettivo", style = T.secondary, color = M.c.ink2)
                    Text("Imposta un obiettivo", style = ts(18, FontWeight.SemiBold), color = M.c.ink)
                }
                Symbol("chevron_right", 22.dp, M.c.ink2)
            }
            return@Card
        }
        val status = Goal.status(trend, today, goalKg, rate, startKg)
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Obiettivo", style = T.secondary, color = M.c.ink2)
                Text("${Fmt.kg(goalKg)} kg", style = ts(22, FontWeight.SemiBold), color = M.c.ink)
            }
            Column(horizontalAlignment = Alignment.End) {
                when {
                    status == null -> {}
                    status.remainingKg <= 0.0 -> Text("Raggiunto ✓", style = T.title15, color = M.c.accent)
                    status.eta != null -> {
                        Text("Con questo trend", style = T.secondary, color = M.c.ink2)
                        Text(Fmt.eta(status.eta), style = T.title15, color = M.c.ink)
                    }
                    else -> {
                        Text("Trend stabile", style = T.title15, color = M.c.ink)
                        Text("ogni passo conta", style = T.secondary, color = M.c.ink2)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeighInsCard(list: List<WeightEntity>, total: Int, modifier: Modifier) {
    val nav = LocalNav.current
    Card(modifier.fillMaxWidth(), padding = PaddingValues(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
        Overline("Pesate")
        Spacer(Modifier.height(8.dp))
        WeighInRows(list)
        if (total > list.size) Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            TextLink("Mostra tutte ($total)", { nav.push(Dest.AllWeights) })
        }
    }
}

@Composable
fun ColumnScope.WeighInRows(list: List<WeightEntity>) {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    list.forEachIndexed { i, w ->
        if (i > 0) Divider(Modifier.fillMaxWidth().padding(end = 8.dp))
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.dayShort(LocalDate.ofEpochDay(w.date)), style = T.body14, color = M.c.ink2, modifier = Modifier.weight(1f))
            Text(Fmt.kg(w.kg), style = ts(16, FontWeight.SemiBold), color = M.c.ink)
            Spacer(Modifier.width(8.dp))
            RoundIconButton("edit", { nav.sheet = SheetKind.Weigh(LocalDate.ofEpochDay(w.date)) }, size = 40.dp, iconSize = 20.dp, bg = M.c.surface, fg = M.c.ink2, contentDescription = "Modifica")
            RoundIconButton("delete", {
                scope.launch {
                    repo.deleteWeight(w.date)
                    toast.show("Pesata eliminata", "Annulla") { scope.launch { repo.restoreWeight(w) } }
                }
            }, size = 40.dp, iconSize = 20.dp, bg = M.c.surface, fg = M.c.ink2, contentDescription = "Elimina")
        }
    }
}

@Composable
fun AllWeightsScreen() {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val weights by repo.weights.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(M.c.bg).verticalScroll(rememberScrollState()).padding(top = statusBarTop() + 6.dp, start = 12.dp, end = 16.dp, bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton("arrow_back", { nav.pop() }, size = 44.dp, bg = M.c.bg, contentDescription = "Indietro")
            Text("Tutte le pesate", style = T.sheetTitle, color = M.c.ink)
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth().padding(start = 4.dp), padding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            WeighInRows((weights ?: emptyList()).reversed())
        }
    }
}
