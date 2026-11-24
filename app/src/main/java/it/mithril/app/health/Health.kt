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
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import it.mithril.app.data.NutritionDayEntity
import it.mithril.app.data.Repo
import it.mithril.app.data.StepsCache
import it.mithril.app.domain.DayNutrition
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.MithrilTheme
import it.mithril.app.ui.theme.T
import it.mithril.app.widget.Widgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import kotlin.math.roundToInt

enum class MealKind(val label: String) {
    BREAKFAST("Colazione"),
    LUNCH("Pranzo"),
    DINNER("Cena"),
    SNACK("Spuntini"),
    OTHER("Altri"),
}

data class MealSummary(
    val kind: MealKind,
    val kcal: Int,
    val proteinG: Float?,
    val carbsG: Float?,
    val fatG: Float?,
)

data class NutritionSource(val packageName: String, val label: String)

data class StepsState(
    val available: Boolean = false,
    val granted: Boolean = false,
    val nutritionGranted: Boolean = false,
    val today: Int? = null,
    /** Passi per giorno (epochDay → passi) delle ultime settimane. */
    val byDay: Map<Long, Int> = emptyMap(),
    val todayNutrition: DayNutrition? = null,
    val todayMeals: List<MealSummary> = emptyList(),
    val nutritionSources: List<NutritionSource> = emptyList(),
    /** Il telefono permette di leggere i dati di più di 30 giorni prima del permesso, e se è concesso. */
    val historyAvailable: Boolean = false,
    val historyGranted: Boolean = false,
) {
    fun average(from: LocalDate, to: LocalDate): Int? {
        val last = minOf(to.toEpochDay(), LocalDate.now().toEpochDay())
        val values = (from.toEpochDay()..last).mapNotNull { byDay[it]?.takeIf { s -> s > 0 } }
        return if (values.isEmpty()) null else values.sum() / values.size
    }
}

/** Lettura di passi e nutrizione da Health Connect (solo lettura, nessun dato esce dal telefono). */
object Health {
    val STEPS_PERMISSION = HealthPermission.getReadPermission(StepsRecord::class)
    val NUTRITION_PERMISSION = HealthPermission.getReadPermission(NutritionRecord::class)
    val PERMISSIONS = setOf(STEPS_PERMISSION)

    /** Permessi da chiedere: passi, nutrizione e, se supportati, background e cronologia. */
    var requestSet: Set<String> = setOf(STEPS_PERMISSION, NUTRITION_PERMISSION)
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
        requestSet = setOf(STEPS_PERMISSION, NUTRITION_PERMISSION) +
            (if (feature(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)) setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND) else emptySet()) +
            (if (historyAvailable) setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY) else emptySet())

        val grantedSet = runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        val stepsGranted = STEPS_PERMISSION in grantedSet
        val nutritionGranted = NUTRITION_PERMISSION in grantedSet
        val historyGranted = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in grantedSet
        if (!stepsGranted && !nutritionGranted) {
            _state.value = StepsState(available = true, granted = false, nutritionGranted = false, historyAvailable = historyAvailable, historyGranted = historyGranted)
            return
        }

        val today = LocalDate.now()
        var byDay = _state.value.byDay
        var todaySteps: Int? = null
        if (stepsGranted) {
            val start = today.minusDays(27)
            val read = runCatching {
                client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(start.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                        timeRangeSlicer = Period.ofDays(1),
                    )
                ).associate { it.startTime.toLocalDate().toEpochDay() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L).toInt() }
            }
            val fresh = read.getOrDefault(emptyMap())
            if (fresh.isNotEmpty()) byDay = fresh
            val aggToday = fresh[today.toEpochDay()] ?: runCatching {
                client.aggregate(
                    AggregateRequest(
                        setOf(StepsRecord.COUNT_TOTAL),
                        TimeRangeFilter.between(today.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                    )
                )[StepsRecord.COUNT_TOTAL]?.toInt()
            }.getOrNull()
            // In background Health Connect può rifiutare la lettura: si tiene l'ultimo valore letto oggi.
            todaySteps = if (read.isSuccess) (aggToday ?: 0).also { StepsCache.save(context, it) } else StepsCache.today(context)
        }

        var todayNutrition = _state.value.todayNutrition
        var todayMeals = _state.value.todayMeals
        var sources = _state.value.nutritionSources
        if (nutritionGranted) {
            syncNutrition(context, today.minusDays(27), today)
            val sourcePkg = Repo.get(context).settings.now().nutritionSource
            val filter = sourceFilter(sourcePkg)
            val recordsToday = runCatching {
                client.readRecords(
                    ReadRecordsRequest(
                        NutritionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(today.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                        dataOriginFilter = filter,
                    )
                ).records
            }.getOrNull()
            if (recordsToday != null) {
                todayMeals = groupMeals(recordsToday)
                val todayRow = Repo.get(context).dao.nutritionDaysNow().firstOrNull { it.date == today.toEpochDay() }
                todayNutrition = todayRow?.let { DayNutrition(it.date, it.kcal, it.proteinG, it.carbsG, it.fatG, it.fiberG) }
                    ?: summarizeRecords(today.toEpochDay(), recordsToday)
            }
            val recentOrigins = runCatching {
                client.readRecords(
                    ReadRecordsRequest(
                        NutritionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(today.minusDays(14).atStartOfDay(), today.plusDays(1).atStartOfDay()),
                    )
                ).records.map { it.metadata.dataOrigin.packageName }.distinct()
            }.getOrNull()
            if (recentOrigins != null) {
                sources = recentOrigins.sorted().map { pkg -> NutritionSource(pkg, appLabel(context, pkg)) }
            }
        } else {
            todayNutrition = null
            todayMeals = emptyList()
            sources = emptyList()
        }

        _state.value = StepsState(
            available = true,
            granted = stepsGranted,
            nutritionGranted = nutritionGranted,
            today = todaySteps,
            byDay = byDay,
            todayNutrition = todayNutrition,
            todayMeals = todayMeals,
            nutritionSources = sources,
            historyAvailable = historyAvailable,
            historyGranted = historyGranted,
        )
    }

    /** Passi per giorno tra [from] e [to] (per le statistiche); vuoto se Health Connect non è leggibile. */
    suspend fun stepsBetween(context: Context, from: LocalDate, to: LocalDate): Map<Long, Int> {
        if (!available(context) || !state.value.granted) return emptyMap()
        val client = HealthConnectClient.getOrCreate(context)
        val out = HashMap<Long, Int>()
        var start = from
        while (!start.isAfter(to)) {
            val end = minOf(start.plusDays(89), to)
            runCatching {
                client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(start.atStartOfDay(), end.plusDays(1).atStartOfDay()),
                        timeRangeSlicer = Period.ofDays(1),
                    )
                ).forEach { out[it.startTime.toLocalDate().toEpochDay()] = (it.result[StepsRecord.COUNT_TOTAL] ?: 0L).toInt() }
            }
            start = end.plusDays(1)
        }
        return out
    }

    /** Sincronizza nella cache Room i totali giornalieri di calorie e macro tra [from] e [to]. */
    suspend fun syncNutrition(context: Context, from: LocalDate, to: LocalDate) {
        if (!available(context) || from.isAfter(to)) return
        val client = HealthConnectClient.getOrCreate(context)
        val repo = Repo.get(context)
        val granted = runCatching { NUTRITION_PERMISSION in client.permissionController.getGrantedPermissions() }.getOrDefault(false)
        if (!granted) return
        val filter = sourceFilter(repo.settings.now().nutritionSource)
        val metrics = setOf(
            NutritionRecord.ENERGY_TOTAL,
            NutritionRecord.PROTEIN_TOTAL,
            NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL,
            NutritionRecord.TOTAL_FAT_TOTAL,
            NutritionRecord.DIETARY_FIBER_TOTAL,
        )
        var start = from
        while (!start.isAfter(to)) {
            val end = minOf(start.plusDays(89), to)
            val result = runCatching {
                client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = metrics,
                        timeRangeFilter = TimeRangeFilter.between(start.atStartOfDay(), end.plusDays(1).atStartOfDay()),
                        timeRangeSlicer = Period.ofDays(1),
                        dataOriginFilter = filter,
                    )
                )
            }
            result.getOrNull()?.let { buckets ->
                val rows = buckets.mapNotNull { b ->
                    val kcal = (b.result[NutritionRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0).roundToInt()
                    if (kcal <= 0) null
                    else NutritionDayEntity(
                        date = b.startTime.toLocalDate().toEpochDay(),
                        kcal = kcal,
                        proteinG = b.result[NutritionRecord.PROTEIN_TOTAL]?.inGrams?.toFloat(),
                        carbsG = b.result[NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL]?.inGrams?.toFloat(),
                        fatG = b.result[NutritionRecord.TOTAL_FAT_TOTAL]?.inGrams?.toFloat(),
                        fiberG = b.result[NutritionRecord.DIETARY_FIBER_TOTAL]?.inGrams?.toFloat(),
                    )
                }
                repo.syncNutritionRange(start.toEpochDay(), end.toEpochDay(), rows)
            }
            start = end.plusDays(1)
        }
    }

    private fun sourceFilter(pkg: String?): Set<DataOrigin> =
        pkg?.takeIf { it.isNotBlank() }?.let { setOf(DataOrigin(it)) } ?: emptySet()

    private fun mealKind(mealType: Int): MealKind = when (mealType) {
        MealType.MEAL_TYPE_BREAKFAST -> MealKind.BREAKFAST
        MealType.MEAL_TYPE_LUNCH -> MealKind.LUNCH
        MealType.MEAL_TYPE_DINNER -> MealKind.DINNER
        MealType.MEAL_TYPE_SNACK -> MealKind.SNACK
        else -> MealKind.OTHER
    }

    private fun groupMeals(records: List<NutritionRecord>): List<MealSummary> {
        if (records.isEmpty()) return emptyList()
        return records.groupBy { mealKind(it.mealType) }
            .entries
            .sortedBy { it.key.ordinal }
            .mapNotNull { (kind, recs) ->
                val kcal = recs.sumOf { it.energy?.inKilocalories ?: 0.0 }.roundToInt()
                val p = recs.mapNotNull { it.protein?.inGrams?.toFloat() }.takeIf { it.isNotEmpty() }?.sum()
                val c = recs.mapNotNull { it.totalCarbohydrate?.inGrams?.toFloat() }.takeIf { it.isNotEmpty() }?.sum()
                val f = recs.mapNotNull { it.totalFat?.inGrams?.toFloat() }.takeIf { it.isNotEmpty() }?.sum()
                if (kcal <= 0 && p == null && c == null && f == null) null
                else MealSummary(kind, kcal, p, c, f)
            }
    }

    private fun summarizeRecords(date: Long, records: List<NutritionRecord>): DayNutrition? {
        val kcal = records.sumOf { it.energy?.inKilocalories ?: 0.0 }.roundToInt()
        if (kcal <= 0) return null
        return DayNutrition(
            date = date,
            kcal = kcal,
            proteinG = records.mapNotNull { it.protein?.inGrams?.toFloat() }.takeIf { it.isNotEmpty() }?.sum(),
            carbsG = records.mapNotNull { it.totalCarbohydrate?.inGrams?.toFloat() }.takeIf { it.isNotEmpty() }?.sum(),
            fatG = records.mapNotNull { it.totalFat?.inGrams?.toFloat() }.takeIf { it.isNotEmpty() }?.sum(),
            fiberG = records.mapNotNull { it.dietaryFiber?.inGrams?.toFloat() }.takeIf { it.isNotEmpty() }?.sum(),
        )
    }

    private fun appLabel(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(info).toString()
    }.getOrDefault(pkg)
}

/** Spiegazione mostrata da Health Connect quando l'utente chiede perché Mithril legge passi e nutrizione. */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MithrilTheme {
                Column(Modifier.fillMaxSize().background(M.c.bg).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
                    Text("Health Connect e Mithril", style = T.screenTitle, color = M.c.ink)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Mithril legge da Health Connect solo il numero di passi e i dati nutrizionali (calorie e macronutrienti registrati ad esempio con MyFitnessPal) per mostrarti i progressi di oggi, le medie settimanali e la correlazione con il trend del peso. " +
                            "I dati restano sul telefono: l'app non ha server e non li invia a nessuno. Puoi revocare qualsiasi permesso dalle impostazioni di Health Connect.",
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
        if (savedInstanceState != null) return
        lifecycleScope.launch {
            if (!Health.available(applicationContext)) { finish(); return@launch }
            Health.refresh(applicationContext)
            launcher.launch(Health.requestSet)
        }
    }
}
