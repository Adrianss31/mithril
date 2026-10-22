package it.mithril.app.health

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import it.mithril.app.data.StepsCache
import it.mithril.app.widget.Widgets
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.MithrilTheme
import it.mithril.app.ui.theme.T

data class StepsState(
    val available: Boolean = false,
    val granted: Boolean = false,
    val today: Int? = null,
    /** Passi per giorno (epochDay → passi) delle ultime settimane. */
    val byDay: Map<Long, Int> = emptyMap(),
    /** Il telefono permette di leggere i passi di più di 30 giorni prima del permesso, e se è concesso. */
    val historyAvailable: Boolean = false,
    val historyGranted: Boolean = false,
) {
    fun average(from: LocalDate, to: LocalDate): Int? {
        val days = (from.toEpochDay()..minOf(to.toEpochDay(), LocalDate.now().toEpochDay())).toList()
        if (days.isEmpty()) return null
        val values = days.mapNotNull { byDay[it] }
        if (values.isEmpty()) return null
        return values.sum() / days.size
    }
}

/** Lettura dei passi da Health Connect (solo lettura, nessun dato esce dal telefono). */
object Health {
    val PERMISSIONS = setOf(HealthPermission.getReadPermission(StepsRecord::class))
    /** Permessi da chiedere: i passi e, se il telefono lo supporta, la lettura in background per il widget. */
    var requestSet: Set<String> = PERMISSIONS
        private set
    private val _state = MutableStateFlow(StepsState())
    val state: StateFlow<StepsState> = _state.asStateFlow()

    fun available(context: Context) = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun refresh(context: Context) {
        if (!available(context)) { _state.value = StepsState(available = false); return }
        val client = HealthConnectClient.getOrCreate(context)
        fun feature(f: Int) = runCatching { client.features.getFeatureStatus(f) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE }.getOrDefault(false)
        val historyAvailable = feature(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY)
        requestSet = PERMISSIONS +
            (if (feature(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)) setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND) else emptySet()) +
            (if (historyAvailable) setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY) else emptySet())
        val grantedSet = runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        val granted = grantedSet.containsAll(PERMISSIONS)
        if (!granted) { _state.value = StepsState(available = true, granted = false, historyAvailable = historyAvailable); return }
        val historyGranted = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in grantedSet
        val today = LocalDate.now()
        val start = today.minusDays(27)
        val read = runCatching {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start.atStartOfDay(), today.atTime(LocalTime.MAX)),
                    timeRangeSlicer = Period.ofDays(1),
                )
            ).associate { it.startTime.toLocalDate().toEpochDay() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L).toInt() }
        }
        val byDay = read.getOrDefault(emptyMap())
        val todaySteps = byDay[today.toEpochDay()] ?: runCatching {
            client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(today.atStartOfDay(), today.atTime(LocalTime.MAX))))[StepsRecord.COUNT_TOTAL]?.toInt()
        }.getOrNull()
        // In background Health Connect può rifiutare la lettura: si tiene l'ultimo valore letto oggi.
        val today0 = if (read.isSuccess) (todaySteps ?: 0).also { StepsCache.save(context, it) } else StepsCache.today(context)
        _state.value = StepsState(available = true, granted = true, today = today0, byDay = byDay.ifEmpty { _state.value.byDay }, historyAvailable = historyAvailable, historyGranted = historyGranted)
    }

    /** Passi per giorno tra [from] e [to] (per le statistiche); vuoto se Health Connect non è leggibile. */
    suspend fun stepsBetween(context: Context, from: LocalDate, to: LocalDate): Map<Long, Int> {
        if (!available(context) || !state.value.granted) return emptyMap()
        val client = HealthConnectClient.getOrCreate(context)
        val out = HashMap<Long, Int>()
        // A blocchi di 90 giorni, per non chiedere troppi intervalli in una volta.
        var start = from
        while (!start.isAfter(to)) {
            val end = minOf(start.plusDays(89), to)
            runCatching {
                client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(start.atStartOfDay(), end.atTime(LocalTime.MAX)),
                        timeRangeSlicer = Period.ofDays(1),
                    )
                ).forEach { out[it.startTime.toLocalDate().toEpochDay()] = (it.result[StepsRecord.COUNT_TOTAL] ?: 0L).toInt() }
            }
            start = end.plusDays(1)
        }
        return out
    }
}

/** Spiegazione mostrata da Health Connect quando l'utente chiede perché Mithril legge i passi. */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MithrilTheme {
                Column(Modifier.fillMaxSize().background(M.c.bg).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
                    Text("Passi e Mithril", style = T.screenTitle, color = M.c.ink)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Mithril legge solo il numero di passi da Health Connect per mostrarti i passi di oggi, la media della settimana nella revisione e lo storico nelle statistiche. " +
                            "I dati restano sul telefono: l'app non ha server e non li invia a nessuno. Puoi revocare il permesso quando vuoi dalle impostazioni di Health Connect.",
                        style = T.body, color = M.c.ink2,
                    )
                    Spacer(Modifier.height(24.dp))
                    PrimaryButton("Ho capito", onClick = { finish() })
                }
            }
        }
    }
}

/** Aperta dal widget dei passi senza permesso: chiede l'accesso a Health Connect e si chiude. */
class HealthPermissionActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(Health.permissionContract()) {
        lifecycleScope.launch {
            Health.refresh(applicationContext)
            Widgets.updateAll(applicationContext)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            if (!Health.available(applicationContext)) { finish(); return@launch }
            Health.refresh(applicationContext)
            launcher.launch(Health.requestSet)
        }
    }
}
