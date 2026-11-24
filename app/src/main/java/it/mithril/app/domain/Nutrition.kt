package it.mithril.app.domain

import it.mithril.app.data.NutritionDayEntity
import java.time.LocalDate
import kotlin.math.roundToInt

data class DayNutrition(
    val date: Long,
    val kcal: Int,
    val proteinG: Float? = null,
    val carbsG: Float? = null,
    val fatG: Float? = null,
    val fiberG: Float? = null,
)

fun NutritionDayEntity.toDomain() = DayNutrition(date, kcal, proteinG, carbsG, fatG, fiberG)

data class CaloriePeriodSummary(
    val daysTracked: Int,
    val avgKcal: Int?,
    val avgProteinG: Int?,
    val avgCarbsG: Int?,
    val avgFatG: Int?,
    val onTargetDays: Int,
    val weekAvgKcal: Int?,
    val weekTrackedDays: Int,
    val weekDeltaKcal: Int?,
)

data class MaintenanceEstimate(
    val maintenanceKcal: Int,
    val avgIntakeKcal: Int,
    val weeklyWeightDeltaKg: Double,
    val daysUsed: Int,
)

/** Calcoli su calorie e macro importati da Health Connect: nessuna stima su giorni non tracciati. */
object Nutrition {
    /** Sotto questa soglia un giorno viene considerato probabilmente incompleto per medie/correlazioni. */
    const val MIN_COMPLETE_KCAL = 500

    fun isOnTarget(kcal: Int, goal: Int): Boolean = kcal <= (goal * 1.05).roundToInt()

    fun summarize(days: List<DayNutrition>, from: LocalDate, today: LocalDate, goalKcal: Int): CaloriePeriodSummary {
        val f = from.toEpochDay()
        val t = today.toEpochDay()
        // Esclude oggi (giorno ancora in corso) e i giorni senza dati.
        val tracked = days.filter { it.date in f until t && it.kcal > 0 }
        val ws = Week.start(today).toEpochDay()
        val weekTracked = days.filter { it.date in ws until t && it.kcal > 0 }

        val proteins = tracked.mapNotNull { it.proteinG?.takeIf { p -> p > 0f } }
        val carbs = tracked.mapNotNull { it.carbsG?.takeIf { c -> c > 0f } }
        val fats = tracked.mapNotNull { it.fatG?.takeIf { fat -> fat > 0f } }

        return CaloriePeriodSummary(
            daysTracked = tracked.size,
            avgKcal = tracked.takeIf { it.isNotEmpty() }?.let { (it.sumOf { d -> d.kcal }.toDouble() / it.size).roundToInt() },
            avgProteinG = proteins.takeIf { it.isNotEmpty() }?.let { (it.sum() / it.size).roundToInt() },
            avgCarbsG = carbs.takeIf { it.isNotEmpty() }?.let { (it.sum() / it.size).roundToInt() },
            avgFatG = fats.takeIf { it.isNotEmpty() }?.let { (it.sum() / it.size).roundToInt() },
            onTargetDays = tracked.count { isOnTarget(it.kcal, goalKcal) },
            weekAvgKcal = weekTracked.takeIf { it.isNotEmpty() }?.let { (it.sumOf { d -> d.kcal }.toDouble() / it.size).roundToInt() },
            weekTrackedDays = weekTracked.size,
            weekDeltaKcal = weekTracked.takeIf { it.isNotEmpty() }?.sumOf { goalKcal - it.kcal },
        )
    }

    /** Media mobile a 7 giorni delle calorie (solo sui giorni con dati > 0). */
    fun rollingKcal(dates: List<Long>, byDay: Map<Long, Int>): List<Int?> = dates.map { d ->
        val window = (d - 6..d).mapNotNull { byDay[it]?.takeIf { k -> k > 0 } }
        if (window.isEmpty()) null else window.sum() / window.size
    }

    /** Stima del fabbisogno reale di mantenimento basata su calorie ingerite e pendenza del trend peso. */
    fun estimateMaintenance(days: List<DayNutrition>, trend: Trend, today: LocalDate, windowDays: Int = 28): MaintenanceEstimate? {
        if (trend.weighIns.size < Trend.MIN_WEIGH_INS) return null
        val t = today.toEpochDay()
        val end = t - 1
        val start = t - windowDays
        if (end <= start) return null
        val complete = days.filter { it.date in start..end && it.kcal >= MIN_COMPLETE_KCAL }
        if (complete.size < 14) return null
        val firstDay = maxOf(start, trend.first)
        if (end - firstDay < 13) return null
        val tStart = trend.at(firstDay) ?: return null
        val tEnd = trend.at(end) ?: return null
        val spanDays = (end - firstDay).toDouble()
        val dailyDeltaKg = (tEnd - tStart) / spanDays
        val avgIntake = (complete.sumOf { it.kcal }.toDouble() / complete.size).roundToInt()
        val maintenance = (avgIntake - dailyDeltaKg * 7700.0).roundToInt()
        if (maintenance !in 1000..5500) return null
        return MaintenanceEstimate(
            maintenanceKcal = maintenance,
            avgIntakeKcal = avgIntake,
            weeklyWeightDeltaKg = dailyDeltaKg * 7.0,
            daysUsed = complete.size,
        )
    }
}
