package it.mithril.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mithril.app.ui.rememberRepo
import it.mithril.app.ui.statusBarTop
import it.mithril.app.ui.theme.rise
import kotlinx.coroutines.launch
import java.time.LocalDate
import it.mithril.app.BuildConfig
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Trend
import it.mithril.app.health.Health
import it.mithril.app.reminders.Reminders
import it.mithril.app.ui.LocalNav
import it.mithril.app.ui.components.Card
import it.mithril.app.ui.components.Chip
import it.mithril.app.ui.components.Divider
import it.mithril.app.ui.components.GrowBar
import it.mithril.app.ui.components.LocalToast
import it.mithril.app.ui.components.Overline
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.components.RoundIconButton
import it.mithril.app.ui.components.TextLink
import it.mithril.app.ui.components.TonalButton
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T
import it.mithril.app.update.UpdateState
import it.mithril.app.update.Updates

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val toast = LocalToast.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repo.appSettings.collectAsStateWithLifecycle()
    val weights by repo.weights.collectAsStateWithLifecycle()
    val steps by Health.state.collectAsStateWithLifecycle()
    val update by Updates.state.collectAsStateWithLifecycle()
    var notifTick by remember { mutableIntStateOf(0) }
    val healthLauncher = rememberLauncherForActivityResult(Health.permissionContract()) { scope.launch { Health.refresh(context) } }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifTick++ }
    LaunchedEffect(Unit) { Health.refresh(context) }
    val trendNow = remember(weights) {
        val w = weights ?: emptyList()
        if (w.size >= Trend.MIN_WEIGH_INS) Trend.of(w.map { it.date to it.kg }, LocalDate.now().toEpochDay()).at(LocalDate.now().toEpochDay()) else w.lastOrNull()?.kg
    }
    fun setGoal(kg: Double?) = scope.launch {
        repo.settings.setGoal(kg?.let { Math.round(it * 10) / 10.0 }, settings.goalStartKg ?: trendNow, LocalDate.now().toEpochDay())
    }

    Column(Modifier.fillMaxSize().background(M.c.bg).verticalScroll(rememberScrollState()).padding(top = statusBarTop() + 6.dp, bottom = 32.dp)) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton("arrow_back", { nav.pop() }, size = 44.dp, bg = M.c.bg, contentDescription = "Indietro")
            Text("Impostazioni", style = T.sheetTitle, color = M.c.ink)
        }
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section("Peso", 0) {
                if (settings.goalKg == null) {
                    Text("Nessun obiettivo impostato. Il trend si vede comunque: l'obiettivo aggiunge la proiezione e il ritmo.", style = T.body14, color = M.c.ink2)
                    Spacer(Modifier.height(12.dp))
                    TonalButton("Imposta un obiettivo", { setGoal(((trendNow ?: 80.0) - 3.0)) }, icon = "add", height = 44.dp)
                } else {
                    StepperRow("Obiettivo", "${Fmt.kg(settings.goalKg!!)} kg", { setGoal(settings.goalKg!! - 0.5) }, { setGoal(settings.goalKg!! + 0.5) })
                    Divider(Modifier.fillMaxWidth())
                    StepperRow(
                        "Ritmo previsto", "${String.format(java.util.Locale.ITALY, "%.2f", settings.weeklyRateKg)} kg/sett.",
                        { scope.launch { repo.settings.setRate((settings.weeklyRateKg - 0.05).coerceAtLeast(0.05)) } },
                        { scope.launch { repo.settings.setRate((settings.weeklyRateKg + 0.05).coerceAtMost(1.0)) } },
                    )
                    settings.goalStartKg?.let { start ->
                        Spacer(Modifier.height(4.dp))
                        Text("Partenza ${Fmt.kg(start)} kg · la barra del widget misura il cammino da qui all'obiettivo.", style = T.caption, color = M.c.ink3)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        TextLink("Riparti da oggi", { scope.launch { repo.settings.setGoal(settings.goalKg, trendNow, LocalDate.now().toEpochDay()); toast.show("Punto di partenza aggiornato") } })
                        Spacer(Modifier.weight(1f))
                        TextLink("Rimuovi obiettivo", { scope.launch { repo.settings.setGoal(null, null, 0) } }, color = M.c.ink2)
                    }
                }
            }
            Section("Passi", 1) {
                StepperRow(
                    "Obiettivo giornaliero", Fmt.thousands(settings.stepsGoal),
                    { scope.launch { repo.settings.setStepsGoal((settings.stepsGoal - 500).coerceAtLeast(1000)) } },
                    { scope.launch { repo.settings.setStepsGoal((settings.stepsGoal + 500).coerceAtMost(30000)) } },
                )
                Divider(Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                when {
                    steps.granted -> Text("Health Connect collegato · lettura dei passi attiva.", style = T.body14, color = M.c.ink2)
                    steps.available -> {
                        Text("Collega Health Connect per vedere i passi in Oggi e nella revisione.", style = T.body14, color = M.c.ink2)
                        Spacer(Modifier.height(10.dp))
                        TonalButton("Collega Health Connect", { healthLauncher.launch(Health.PERMISSIONS) }, height = 44.dp)
                    }
                    else -> Text("Health Connect non è disponibile su questo telefono.", style = T.body14, color = M.c.ink2)
                }
            }
            Section("Promemoria", 2) {
                Text("Avviso prima della sessione", style = T.title15, color = M.c.ink)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 15, 30, 45, 60).forEach { m -> Chip("$m'", settings.reminderLeadMin == m, { scope.launch { repo.settings.setReminderLead(m) } }) }
                }
                Spacer(Modifier.height(14.dp))
                Divider(Modifier.fillMaxWidth())
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Revisione della domenica", style = T.title15, color = M.c.ink)
                        Text("Notifica alle 20:00 per chiudere la settimana", style = T.secondary, color = M.c.ink2)
                    }
                    Switch(
                        settings.reviewReminder, { on -> scope.launch { repo.settings.setReviewReminder(on) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = M.c.accent, checkedThumbColor = M.c.onAccent, uncheckedTrackColor = M.c.surface2, uncheckedBorderColor = M.c.line2, uncheckedThumbColor = M.c.ink3),
                    )
                }
                val canNotify = remember(notifTick) { Reminders.canNotify(context) }
                if (!canNotify && Build.VERSION.SDK_INT >= 33) {
                    TonalButton("Consenti le notifiche", { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }, height = 44.dp)
                }
            }
            Section("Aggiornamenti", 3) {
                Text("Mithril ${BuildConfig.VERSION_NAME}", style = T.title15, color = M.c.ink)
                Spacer(Modifier.height(4.dp))
                when (val u = update) {
                    UpdateState.Idle -> Text("Gli aggiornamenti arrivano da GitHub, firmati e verificati.", style = T.body14, color = M.c.ink2)
                    UpdateState.Checking -> Text("Controllo in corso…", style = T.body14, color = M.c.ink2)
                    UpdateState.UpToDate -> Text("Hai l'ultima versione.", style = T.body14, color = M.c.ink2)
                    is UpdateState.Available -> {
                        Text("Disponibile la versione ${u.info.versionName}.", style = T.body14.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = M.c.accentInk)
                        if (u.info.notes.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(u.info.notes.trim().take(1200), style = T.secondary, color = M.c.ink2)
                        }
                    }
                    is UpdateState.Downloading -> {
                        Text("Scarico la versione ${u.info.versionName}… ${(u.progress * 100).toInt()}%", style = T.body14, color = M.c.ink2)
                        Spacer(Modifier.height(10.dp))
                        GrowBar(u.progress, Modifier.fillMaxWidth(), height = 8.dp, delayMs = 0)
                    }
                    is UpdateState.Ready -> Text("Versione ${u.info.versionName} pronta da installare.", style = T.body14, color = M.c.ink2)
                    is UpdateState.Failed -> Text(u.message, style = T.body14, color = M.c.accentInk)
                }
                Spacer(Modifier.height(14.dp))
                when (val u = update) {
                    is UpdateState.Available -> PrimaryButton("Scarica e installa", { scope.launch { Updates.download(context, u.info) } }, Modifier.fillMaxWidth(), icon = "download", iconFilled = false, height = 48.dp)
                    is UpdateState.Ready -> PrimaryButton("Installa", { runCatching { context.startActivity(Updates.installIntent(context, u.file)) } }, Modifier.fillMaxWidth(), icon = "system_update", iconFilled = false, height = 48.dp)
                    is UpdateState.Failed -> if (u.info != null) TonalButton("Riprova il download", { scope.launch { Updates.download(context, u.info) } }, height = 44.dp)
                        else TonalButton("Cerca aggiornamenti", { scope.launch { Updates.check(context) } }, icon = "sync", height = 44.dp)
                    is UpdateState.Downloading, UpdateState.Checking -> {}
                    else -> TonalButton("Cerca aggiornamenti", { scope.launch { Updates.check(context) } }, icon = "sync", height = 44.dp)
                }
            }
            Section("Privacy", 4) {
                Text("Tutto resta sul telefono: niente account, niente server. Internet serve solo per i video di YouTube e per controllare gli aggiornamenti.", style = T.body14, color = M.c.ink2)
            }
        }
    }
}

@Composable
private fun Section(title: String, index: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.rise(index)) {
        Overline(title, Modifier.padding(start = 8.dp, bottom = 8.dp))
        Card(Modifier.fillMaxWidth(), radius = 22.dp, padding = PaddingValues(16.dp), content = content)
    }
}

@Composable
private fun StepperRow(label: String, value: String, dec: () -> Unit, inc: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.title15, color = M.c.ink, modifier = Modifier.weight(1f))
        RoundIconButton("remove", dec, size = 36.dp, iconSize = 20.dp, contentDescription = "Meno")
        Box(Modifier.width(104.dp), contentAlignment = Alignment.Center) { Text(value, style = T.title15, color = M.c.ink) }
        RoundIconButton("add", inc, size = 36.dp, iconSize = 20.dp, contentDescription = "Più")
    }
}
