package it.mithril.app.ui.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mithril.app.ui.components.dashedBorder
import it.mithril.app.ui.rememberRepo
import it.mithril.app.ui.statusBarTop
import it.mithril.app.ui.theme.rise
import it.mithril.app.ui.theme.ts
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import it.mithril.app.data.SlotEntity
import it.mithril.app.data.VideoEntity
import it.mithril.app.domain.DayState
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Sessions
import it.mithril.app.domain.Week
import it.mithril.app.ui.BottomGap
import it.mithril.app.ui.Dest
import it.mithril.app.ui.LocalNav
import it.mithril.app.ui.SheetKind
import it.mithril.app.ui.components.Card
import it.mithril.app.ui.components.HeroDays
import it.mithril.app.ui.components.NumberBadge
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.components.RoundIconButton
import it.mithril.app.ui.components.SmallChip
import it.mithril.app.ui.components.Symbol
import it.mithril.app.ui.components.TextLink
import it.mithril.app.ui.components.TonalButton
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T

/** Settimana da pianificare dal planner: quella mostrata se futura, la prossima da venerdì in poi, altrimenti questa. */
fun planTarget(today: LocalDate, shown: LocalDate): LocalDate = when {
    shown.isAfter(Week.start(today)) -> shown
    today.dayOfWeek >= DayOfWeek.FRIDAY -> Week.start(today).plusWeeks(1)
    else -> Week.start(today)
}

@Composable
fun PlannerScreen() {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val today by repo.today.collectAsStateWithLifecycle()
    val slots by repo.slots.collectAsStateWithLifecycle()
    val logs by repo.logs.collectAsStateWithLifecycle()
    val videos by repo.videos.collectAsStateWithLifecycle()
    val plans by repo.weekPlans.collectAsStateWithLifecycle()
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val goals = remember(plans) { plans.associate { it.weekStart to it.goalSessions } }
    val start = Week.start(today).plusWeeks(offset.toLong())
    val goal = Week.goalFor(start, goals)
    val summary = remember(start, today, slots, logs, goal) { Week.summary(start, today, slots, logs, goal) }
    val nextStart = Week.start(today).plusWeeks(1)
    val nextPlanned = slots.count { it.date in nextStart.toEpochDay()..nextStart.toEpochDay() + 6 }
    val all = videos ?: emptyList()
    val epoch = nav.tabEpoch * 100 + offset

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(M.c.hero)
                .padding(start = 20.dp, end = 20.dp, top = statusBarTop() + 12.dp, bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Planner", style = ts(26, FontWeight.SemiBold, (-0.02).em), color = M.c.onHero)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundIconButton("chevron_left", { offset-- }, size = 26.dp, iconSize = 18.dp, bg = M.c.heroSoft.copy(alpha = 0f), fg = M.c.onHero, enabled = offset > -8, contentDescription = "Settimana precedente")
                        Text(Fmt.weekRange(start), style = T.secondary, color = M.c.onHero.copy(alpha = 0.9f))
                        RoundIconButton("chevron_right", { offset++ }, size = 26.dp, iconSize = 18.dp, bg = M.c.heroSoft.copy(alpha = 0f), fg = M.c.onHero, enabled = offset < 4, contentDescription = "Settimana successiva")
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Obiettivo", style = T.caption, color = M.c.onHero.copy(alpha = 0.8f))
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundIconButton("remove", { scope.launch { repo.setWeekGoal(start, goal - 1) } }, size = 32.dp, iconSize = 18.dp, bg = M.c.heroSoft, fg = M.c.onHero, enabled = goal > 1, contentDescription = "Meno sessioni")
                        Text(if (goal == 1) "1 sessione" else "$goal sessioni", style = T.title15, color = M.c.onHero, modifier = Modifier.padding(horizontal = 10.dp))
                        RoundIconButton("add", { scope.launch { repo.setWeekGoal(start, goal + 1) } }, size = 32.dp, iconSize = 18.dp, bg = M.c.heroSoft, fg = M.c.onHero, enabled = goal < 7, contentDescription = "Più sessioni")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            HeroDays(summary.days, key = epoch)
            Spacer(Modifier.height(14.dp))
            Text(
                if (summary.active) "${summary.sessions} di ${summary.goal} — Settimana attiva ✓" else "${summary.sessions} di ${summary.goal} sessioni",
                style = T.secondary, color = M.c.onHero.copy(alpha = 0.9f),
            )
        }
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.rise(0, epoch), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Pianifica la settimana", { nav.push(Dest.PlanWeek(planTarget(today, start))) }, Modifier.weight(1f), icon = "edit_calendar", iconFilled = false, height = 48.dp)
                TonalButton("Revisione", { nav.push(Dest.Review(Week.reviewWeek(today))) }, icon = "insights", height = 48.dp)
            }
            if (offset == 0 && nextPlanned > 0) {
                Box(
                    Modifier.rise(1, epoch).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(M.c.accentSoft)
                        .clickable { offset = 1 }.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        "Prossima settimana pianificata ✓ · ${if (nextPlanned == 1) "1 sessione" else "$nextPlanned sessioni"} dal ${nextStart.dayOfMonth} ${Fmt.month(nextStart)}",
                        style = T.body14.copy(fontWeight = FontWeight.Medium), color = M.c.accentInk,
                    )
                }
            }
            if (offset != 0) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextLink("Torna a questa settimana", { offset = 0 })
                }
            }
            summary.days.forEachIndexed { i, day ->
                val daySlots = slots.filter { it.date == day.date.toEpochDay() }.sortedBy { it.timeMin }
                DayCard(day.date, day.state, day.isToday, !day.date.isBefore(today), daySlots, all, Modifier.rise(i + 2, epoch),
                    onAdd = { nav.sheet = SheetKind.NewSlot(day.date) },
                    onSlot = { s -> nav.sheet = SheetKind.NewSlot(day.date, edit = s) })
            }
            Spacer(Modifier.height(BottomGap))
        }
    }
}

@Composable
private fun DayCard(
    date: LocalDate,
    state: DayState,
    isToday: Boolean,
    editable: Boolean,
    slots: List<SlotEntity>,
    all: List<VideoEntity>,
    modifier: Modifier,
    onAdd: () -> Unit,
    onSlot: (SlotEntity) -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        radius = 22.dp,
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        border = if (isToday) androidx.compose.foundation.BorderStroke(1.5.dp, M.c.accent) else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.dayShortName(date.dayOfWeek), style = ts(16, FontWeight.Bold), color = M.c.ink)
            Spacer(Modifier.width(6.dp))
            Text(Fmt.dateShort(date), style = T.secondary, color = M.c.ink2)
            if (isToday) { Spacer(Modifier.width(8.dp)); Text("OGGI", style = ts(11, FontWeight.Bold), color = M.c.accent) }
            Spacer(Modifier.weight(1f))
            when (state) {
                DayState.DONE -> SmallChip("Fatta")
                DayState.MINI -> SmallChip("Mini fatta")
                DayState.PLANNED -> SmallChip("Pianificata", bg = M.c.surface, fg = M.c.ink2, border = M.c.line2)
                DayState.SKIPPED -> SmallChip("Saltata", bg = M.c.surface, fg = M.c.ink2, border = M.c.line2, dashed = true)
                DayState.REST -> {}
            }
        }
        if (slots.isEmpty()) {
            Spacer(Modifier.height(if (editable) 8.dp else 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (editable) "Riposo · niente in programma" else "Riposo", style = T.secondary, color = M.c.ink3, modifier = Modifier.weight(1f))
                if (editable) {
                    Row(
                        Modifier.height(36.dp).dashedBorder(M.c.line2, 18.dp, 1.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onAdd).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Symbol("add", 18.dp, M.c.ink)
                        Spacer(Modifier.width(4.dp))
                        Text("Aggiungi", style = T.button15.copy(fontSize = 14.sp), color = M.c.ink)
                    }
                }
            }
        } else slots.forEach { s ->
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp)).clickable { onSlot(s) },
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.width(64.dp)) {
                    Text(Fmt.time(s.timeMin), style = T.cardTitle, color = M.c.ink)
                    Text(s.place, style = T.caption, color = M.c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.padding(horizontal = 10.dp).width(2.dp).fillMaxHeight().background(M.c.line))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Sessions.videosOf(s.videoIdList, all).forEachIndexed { k, v ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NumberBadge(k + 1)
                            Spacer(Modifier.width(8.dp))
                            Text(v.title, style = T.body14, color = M.c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** "in soggiorno" / "fuori" / "in Palestra condominio". */
object Places {
    val DEFAULTS = listOf("Soggiorno", "Camera", "Fuori")
    fun phrase(place: String): String = when {
        place.equals("Fuori", true) -> "fuori"
        place in DEFAULTS -> "in ${place.lowercase()}"
        else -> "in $place"
    }
    fun intention(date: LocalDate, timeMin: Int, place: String, titles: List<String>): String =
        "${Fmt.dayName(date.dayOfWeek)} alle ${Fmt.time(timeMin)}, ${phrase(place)}: ${titles.joinToString(" + ")}."
}
