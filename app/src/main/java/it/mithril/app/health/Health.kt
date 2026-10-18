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
    private val _state = MutableStateFlow(StepsState())
    val state: StateFlow<StepsState> = _state.asStateFlow()

    fun available(context: Context) = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun refresh(context: Context) {
        if (!available(context)) { _state.value = StepsState(available = false); return }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = runCatching { client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS) }.getOrDefault(false)
        if (!granted) { _state.value = StepsState(available = true, granted = false); return }
        val today = LocalDate.now()
        val start = today.minusDays(27)
        val byDay = runCatching {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start.atStartOfDay(), today.atTime(LocalTime.MAX)),
                    timeRangeSlicer = Period.ofDays(1),
                )
            ).associate { it.startTime.toLocalDate().toEpochDay() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L).toInt() }
        }.getOrDefault(emptyMap())
        val todaySteps = byDay[today.toEpochDay()] ?: runCatching {
            client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(today.atStartOfDay(), today.atTime(LocalTime.MAX))))[StepsRecord.COUNT_TOTAL]?.toInt()
        }.getOrNull()
        _state.value = StepsState(available = true, granted = true, today = todaySteps ?: 0, byDay = byDay)
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
                        "Mithril legge solo il numero di passi da Health Connect per mostrarti i passi di oggi e la media della settimana nella revisione. " +
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
