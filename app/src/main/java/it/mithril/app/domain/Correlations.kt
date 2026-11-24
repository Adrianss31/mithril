package it.mithril.app.domain

import it.mithril.app.data.SessionLogEntity
import it.mithril.app.data.SlotEntity
import java.time.LocalDate
import kotlin.math.roundToInt

data class WeekPoint(
    val start: LocalDate,
    val sessions: Int,
    val goal: Int,
    val active: Boolean,
    val weightDeltaKg: Double?,
    val avgKcal: Int?,
    val kcalDays: Int,
)

data class CorrelationSummary(
    val weeks: List<WeekPoint>,
    val activeWeightAvg: Double?,
    val activeWeeksCount: Int,
    val inactiveWeightAvg: Double?,
    val inactiveWeeksCount: Int,
    val onTargetKcalWeightAvg: Double?,
    val onTargetKcalWeeks: Int,
    val overTargetKcalWeightAvg: Double?,
    val overTargetKcalWeeks: Int,
    val trainingDayKcalAvg: Int?,
    val restDayKcalAvg: Int?,
)

/** Incrocia settimane attive, calorie ingerite e variazione reale del trend peso. */
object Correlations {
    fun analyze(
        from: LocalDate,
        today: LocalDate,
        slots: List<SlotEntity>,
        logs: List<SessionLogEntity>,
        goals: Map<Long, Int>,
        trend: Trend?,
        nutrition: List<DayNutrition>,
        caloriesGoal: Int,
    ): CorrelationSummary {
        val lastClosedStart = Week.start(today).minusWeeks(1)
        val firstStart = Week.start(from)
        if (lastStartInvalid(firstStart, lastClosedStart)) {
            return CorrelationSummary(emptyList(), null, 0, null, 0, null, 0, null, 0, null, null)
        }
        val logsByDay = logs.groupBy { it.date }
        val nutritionByDay = nutrition.associateBy { it.date }
        val validTrend = trend?.takeIf { it.weighIns.size >= Trend.MIN_WEIGH_INS }

        val weeks = generateSequence(firstStart) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(lastClosedStart) }
            .map { ws ->
                val f = ws.toEpochDay()
                val t = f + 6
                val goal = Week.goalFor(ws, goals)
                val sessions = (f..t).sumOf { d -> logsByDay[d]?.size ?: 0 }
                val active = sessions >= goal
                val weightDelta = validTrend?.let { tr ->
                    val before = tr.at(f - 1)
                    val after = tr.at(t)
                    if (before != null && after != null && f - 1 >= tr.first) after - before else null
                }
                val completeDays = (f..t).mapNotNull { d -> nutritionByDay[d]?.takeIf { it.kcal >= Nutrition.MIN_COMPLETE_KCAL } }
                val avgKcal = if (completeDays.size >= 4) (completeDays.sumOf { it.kcal }.toDouble() / completeDays.size).roundToInt() else null
                WeekPoint(ws, sessions, goal, active, weightDelta, avgKcal, completeDays.size)
            }
            .toList()

        val withWeight = weeks.filter { it.weightDeltaKg != null }
        val activeW = withWeight.filter { it.active }.map { it.weightDeltaKg!! }
        val inactiveW = withWeight.filter { !it.active }.map { it.weightDeltaKg!! }

        val withWeightAndKcal = withWeight.filter { it.avgKcal != null }
        val onTargetW = withWeightAndKcal.filter { Nutrition.isOnTarget(it.avgKcal!!, caloriesGoal) }.map { it.weightDeltaKg!! }
        val overTargetW = withWeightAndKcal.filter { !Nutrition.isOnTarget(it.avgKcal!!, caloriesGoal) }.map { it.weightDeltaKg!! }

        val fDay = from.toEpochDay()
        val tDay = today.toEpochDay()
        val completePeriod = nutrition.filter { it.date in fDay until tDay && it.kcal >= Nutrition.MIN_COMPLETE_KCAL }
        val logDays = logsByDay.keys
        val trainingKcal = completePeriod.filter { it.date in logDays }.map { it.kcal }
        val restKcal = completePeriod.filter { it.date !in logDays }.map { it.kcal }

        return CorrelationSummary(
            weeks = weeks,
            activeWeightAvg = activeW.takeIf { it.isNotEmpty() }?.average(),
            activeWeeksCount = activeW.size,
            inactiveWeightAvg = inactiveW.takeIf { it.isNotEmpty() }?.average(),
            inactiveWeeksCount = inactiveW.size,
            onTargetKcalWeightAvg = onTargetW.takeIf { it.isNotEmpty() }?.average(),
            onTargetKcalWeeks = onTargetW.size,
            overTargetKcalWeightAvg = overTargetW.takeIf { it.isNotEmpty() }?.average(),
            overTargetKcalWeeks = overTargetW.size,
            trainingDayKcalAvg = trainingKcal.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            restDayKcalAvg = restKcal.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        )
    }

    private fun lastStartInvalid(first: LocalDate, last: LocalDate) = first.isAfter(last)
}
