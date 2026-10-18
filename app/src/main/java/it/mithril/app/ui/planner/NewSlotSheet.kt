package it.mithril.app.ui.planner

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mithril.app.ui.rememberRepo
import it.mithril.app.ui.theme.ts
import kotlinx.coroutines.launch
import it.mithril.app.data.SlotEntity
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.TimeSuggest
import it.mithril.app.domain.Sessions
import it.mithril.app.ui.SheetKind
import it.mithril.app.ui.components.Chip
import it.mithril.app.ui.components.LocalToast
import it.mithril.app.ui.components.OutlineButton
import it.mithril.app.ui.components.Overline
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.components.RoundIconButton
import it.mithril.app.ui.components.Symbol
import it.mithril.app.ui.theme.Ease
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T

/** Foglio "Nuovo slot" in 3 passi: cosa (in ordine), quando, dove. Con [SheetKind.NewSlot.onlyWhenWhere] solo ora e luogo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.NewSlotSheet(s: SheetKind.NewSlot, onDone: () -> Unit) {
    val repo = rememberRepo()
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val videos by repo.videos.collectAsStateWithLifecycle()
    val slots by repo.slots.collectAsStateWithLifecycle()
    val all = videos ?: emptyList()
    val first = if (s.onlyWhenWhere) 1 else 0
    var step by remember(s) { mutableIntStateOf(first) }
    val picked = remember(s) { mutableStateListOf<Long>().apply { s.edit?.let { addAll(it.videoIdList) } } }
    var time by remember(s) { mutableIntStateOf(s.edit?.timeMin ?: 18 * 60 + 30) }
    var place by remember(s) { mutableStateOf(s.edit?.place ?: "Soggiorno") }
    var custom by remember(s) { mutableStateOf(false) }
    val times = remember(slots) { TimeSuggest.suggest(slots, java.time.LocalDate.now()) }
    val knownPlaces = remember(slots) { (Places.DEFAULTS + slots.map { it.place }).distinct().take(6) }
    LaunchedEffect(s) {
        if (s.edit == null) repo.lastSlotLike(s.date)?.let { time = it.timeMin; place = it.place }
    }
    val titles = Sessions.videosOf(picked.toList(), all).map { it.title }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(Fmt.dayLong(s.date), style = T.sheetTitle, color = M.c.ink, modifier = Modifier.weight(1f))
        if (s.edit != null && !s.onlyWhenWhere) {
            RoundIconButton("delete", {
                scope.launch { repo.deleteSlot(s.edit.id); toast.show("Slot eliminato") }
                onDone()
            }, size = 36.dp, iconSize = 20.dp, contentDescription = "Elimina slot")
            Spacer(Modifier.width(10.dp))
        }
        Text(if (s.onlyWhenWhere) "Passo ${step} di 2" else "Passo ${step + 1} di 3", style = T.secondary, color = M.c.ink2)
    }
    Spacer(Modifier.height(16.dp))
    val motion = M.motion
    AnimatedContent(
        step,
        transitionSpec = {
            if (!motion) EnterTransition.None togetherWith ExitTransition.None
            else (fadeIn(tween(400, easing = Ease.Standard)) + slideInHorizontally(tween(400, easing = Ease.Standard)) { 60 }) togetherWith fadeOut(tween(120))
        },
        modifier = Modifier.weight(1f, fill = false),
        label = "slotStep",
    ) { st ->
        Column {
            when (st) {
                0 -> {
                    Overline("Cosa · in ordine di riproduzione")
                    Spacer(Modifier.height(10.dp))
                    if (all.isEmpty()) Text("La Libreria è vuota: salva prima qualche video da YouTube.", style = T.body14, color = M.c.ink2)
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        all.forEach { v ->
                            val idx = picked.indexOf(v.id)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                                    if (idx >= 0) picked.remove(v.id) else if (picked.size < 3) picked.add(v.id) else toast.show("Massimo 3 video per sessione")
                                }.padding(vertical = 9.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(26.dp).clip(CircleShape)
                                        .then(if (idx >= 0) Modifier.background(M.c.accent) else Modifier.border(1.5.dp, M.c.line2, CircleShape)),
                                    contentAlignment = Alignment.Center,
                                ) { if (idx >= 0) Text("${idx + 1}", style = ts(13, FontWeight.Bold), color = M.c.onAccent) }
                                Spacer(Modifier.width(12.dp))
                                Text(v.title, style = T.title15, color = M.c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.width(8.dp))
                                Text(Fmt.clock(v.durationSec), style = T.secondary, color = M.c.ink3)
                            }
                        }
                    }
                }
                1 -> {
                    Overline("Quando")
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        times.forEach { t -> Chip(Fmt.time(t), time == t, { time = t }) }
                        val other = time !in times
                        Chip(if (other) Fmt.time(time) else "Altro…", other, {
                            TimePickerDialog(context, { _, h, m -> time = h * 60 + m }, time / 60, time % 60, true).show()
                        })
                    }
                }
                else -> {
                    Overline("Dove")
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        knownPlaces.forEach { p -> Chip(p, !custom && place == p, { place = p; custom = false }) }
                        Chip("+ Personalizzato", custom, { custom = true; if (place in knownPlaces) place = "" })
                    }
                    if (custom) {
                        Spacer(Modifier.height(12.dp))
                        PlaceField(place) { place = it }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(M.c.surface2).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            if (titles.isEmpty() && !s.onlyWhenWhere) "Scegli uno o più video: verranno riprodotti in sequenza."
            else Places.intention(s.date, time, place.ifBlank { "…" }, if (s.onlyWhenWhere) listOf("…") else titles),
            style = T.body14, color = M.c.ink,
        )
    }
    Spacer(Modifier.height(16.dp))
    val last = step == 2
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (step > first) OutlineButton("Indietro", { step-- }, Modifier.weight(0.8f), height = 54.dp)
        PrimaryButton(
            when {
                !last -> "Avanti"
                s.onlyWhenWhere || s.edit != null -> "Salva"
                else -> "Aggiungi al planner"
            },
            {
                if (!last) step++
                else if (s.onlyWhenWhere) { s.onPicked?.invoke(time, place.trim().ifBlank { "Soggiorno" }); onDone() }
                else {
                    val slot = SlotEntity(id = s.edit?.id ?: 0, date = s.date.toEpochDay(), timeMin = time, place = place.trim().ifBlank { "Soggiorno" }, videoIds = picked.joinToString(","))
                    scope.launch {
                        repo.saveSlot(slot)
                        toast.show(if (s.edit != null) "Slot aggiornato" else "Aggiunto: ${Fmt.dayName(s.date.dayOfWeek).lowercase()} alle ${Fmt.time(time)}")
                    }
                    onDone()
                }
            },
            Modifier.weight(1.4f),
            enabled = s.onlyWhenWhere || picked.isNotEmpty(),
        )
    }
}

@Composable
fun PlaceField(value: String, onChange: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(M.c.surface2).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text("es. Terrazzo, Palestra condominio", style = T.body, color = M.c.ink3)
        BasicTextField(
            value, { onChange(it.take(40)) },
            textStyle = T.body.copy(color = M.c.ink),
            singleLine = true,
            cursorBrush = SolidColor(M.c.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
