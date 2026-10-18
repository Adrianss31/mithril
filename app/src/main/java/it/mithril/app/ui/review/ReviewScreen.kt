package it.mithril.app.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mithril.app.ui.rememberRepo
import it.mithril.app.ui.statusBarTop
import it.mithril.app.ui.theme.rise
import kotlinx.coroutines.launch
import java.time.LocalDate
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Week
import it.mithril.app.health.Health
import it.mithril.app.ui.Dest
import it.mithril.app.ui.LocalNav
import it.mithril.app.ui.components.Card
import it.mithril.app.ui.components.Chip
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.components.ReviewDays
import it.mithril.app.ui.components.RoundIconButton
import it.mithril.app.ui.planner.WeightAndSteps
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T

private val OBSTACLES = listOf("Stanchezza", "Orario sbagliato", "Imprevisto", "Voglia", "Altro")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(start: LocalDate) {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val today by repo.today.collectAsStateWithLifecycle()
    val slots by repo.slots.collectAsStateWithLifecycle()
    val logs by repo.logs.collectAsStateWithLifecycle()
    val weights by repo.weights.collectAsStateWithLifecycle()
    val reviews by repo.reviews.collectAsStateWithLifecycle()
    val settings by repo.appSettings.collectAsStateWithLifecycle()
    val steps by Health.state.collectAsStateWithLifecycle()
    val s = remember(slots, logs, today) { Week.summary(start, today, slots, logs, repo.weekGoal(start)) }
    val saved = reviews.firstOrNull { it.weekStart == start.toEpochDay() }
    var obstacle by rememberSaveable(saved?.obstacle) { mutableStateOf(saved?.obstacle) }
    val skipped = s.skippedDays.lastOrNull()

    Column(Modifier.fillMaxSize().background(M.c.bg).verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)).background(M.c.hero)
                .padding(start = 8.dp, end = 20.dp, top = statusBarTop() + 6.dp, bottom = 24.dp)
        ) {
            RoundIconButton("arrow_back", { nav.pop() }, size = 44.dp, bg = M.c.hero, fg = M.c.onHero, contentDescription = "Indietro")
            Column(Modifier.padding(start = 12.dp)) {
                Text("Revisione · ${Fmt.weekRange(start)}", style = T.secondary, color = M.c.onHero.copy(alpha = 0.8f))
                Spacer(Modifier.height(6.dp))
                Text(
                    if (s.active) "Settimana attiva ✓ — ${s.sessions} ${if (s.sessions == 1) "sessione" else "sessioni"} su ${s.goal}."
                    else "${s.sessions} ${if (s.sessions == 1) "sessione" else "sessioni"} su ${s.goal}. Ogni sessione conta.",
                    style = T.heroTitleL, color = M.c.onHero,
                )
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.rise(0).fillMaxWidth()) {
                Text("Sessioni", style = T.secondary, color = M.c.ink2)
                Spacer(Modifier.height(12.dp))
                ReviewDays(s.days)
                Spacer(Modifier.height(12.dp))
                Text("Pianificate ${s.planned} · Fatte ${s.done} · Mini ${s.mini}", style = T.secondary, color = M.c.ink2)
            }
            WeightAndSteps(start, weights?.map { it.date to it.kg } ?: emptyList(), today, steps, settings.stepsGoal, Modifier.rise(1), spark = true)
            if (skipped != null) Card(Modifier.rise(2).fillMaxWidth()) {
                Text("${Fmt.dayName(skipped.dayOfWeek)} è saltato. Cosa ha ostacolato?", style = T.title16, color = M.c.ink)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OBSTACLES.forEach { o ->
                        Chip(o, obstacle == o, {
                            obstacle = o
                            scope.launch { repo.saveReview(start, o, skipped) }
                        })
                    }
                }
                AnimatedVisibility(obstacle != null, enter = fadeIn(tween(300)) + expandVertically(tween(300))) {
                    Text(
                        when (obstacle) {
                            "Orario sbagliato" -> "Proviamo un altro orario nel planner."
                            "Stanchezza" -> "Ci sta. La versione mini è lì apposta."
                            else -> "Grazie. Ne terremo conto pianificando."
                        },
                        style = T.body14, color = M.c.accentInk,
                        modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).background(M.c.accentSoft).padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            Text(
                if (s.active) "Costanza, non perfezione. Bel lavoro." else "Si riparte lunedì: mai due volte di fila.",
                style = T.cardTitle, color = M.c.ink, modifier = Modifier.rise(3).padding(horizontal = 4.dp, vertical = 6.dp),
            )
            val next = start.plusWeeks(1)
            Row(Modifier.rise(4), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(
                    if (next.isAfter(Week.start(today))) "Pianifica la prossima settimana" else "Pianifica la settimana",
                    { nav.replace(Dest.PlanWeek(next)) },
                    Modifier.fillMaxWidth(), icon = "edit_calendar", iconFilled = false,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
