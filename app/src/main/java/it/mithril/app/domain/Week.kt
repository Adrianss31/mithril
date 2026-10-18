package it.mithril.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import it.mithril.app.data.SessionLogEntity
import it.mithril.app.data.SlotEntity

enum class DayState { DONE, MINI, PLANNED, SKIPPED, REST }

data class DayInfo(val date: LocalDate, val state: DayState, val isToday: Boolean)

data class WeekSummary(
    val start: LocalDate,
    val days: List<DayInfo>,
    val goal: Int,
    val done: Int,
    val mini: Int,
    val planned: Int,
) {
    val sessions: Int get() = done + mini
    val active: Boolean get() = sessions >= goal
    val skippedDays: List<LocalDate> get() = days.filter { it.state == DayState.SKIPPED }.map { it.date }
}

/** Regole della settimana (lunedì–domenica): stati dei giorni, settimane attive, "mai due volte di fila". */
object Week {
    const val DEFAULT_GOAL = 3

    fun start(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun dayState(date: LocalDate, today: LocalDate, slots: List<SlotEntity>, logs: List<SessionLogEntity>): DayState {
        val d = date.toEpochDay()
        val dayLogs = logs.filter { it.date == d }
        return when {
            dayLogs.any { !it.isMini } -> DayState.DONE
            dayLogs.isNotEmpty() -> DayState.MINI
            slots.none { it.date == d } -> DayState.REST
            date.isBefore(today) -> DayState.SKIPPED
            else -> DayState.PLANNED
        }
    }

    fun summary(start: LocalDate, today: LocalDate, slots: List<SlotEntity>, logs: List<SessionLogEntity>, goal: Int): WeekSummary {
        val from = start.toEpochDay(); val to = from + 6
        val weekSlots = slots.filter { it.date in from..to }
        val weekLogs = logs.filter { it.date in from..to }
        val days = (0L..6L).map { i ->
            val date = start.plusDays(i)
            DayInfo(date, dayState(date, today, weekSlots, weekLogs), date == today)
        }
        return WeekSummary(
            start = start,
            days = days,
            goal = goal,
            done = weekLogs.count { !it.isMini },
            mini = weekLogs.count { it.isMini },
            planned = weekSlots.size,
        )
    }

    /** Obiettivo della settimana: quello salvato, altrimenti quello dell'ultima settimana pianificata prima. */
    fun goalFor(start: LocalDate, goals: Map<Long, Int>, fallback: Int = DEFAULT_GOAL): Int {
        goals[start.toEpochDay()]?.let { return it }
        return goals.filterKeys { it < start.toEpochDay() }.maxByOrNull { it.key }?.value ?: fallback
    }

    /**
     * Settimane attive consecutive. La settimana in corso conta solo se è già attiva:
     * finché non è finita non interrompe la serie.
     */
    fun activeStreak(today: LocalDate, slots: List<SlotEntity>, logs: List<SessionLogEntity>, goals: Map<Long, Int>): Int {
        val first = logs.minOfOrNull { it.date } ?: return 0
        var week = start(today)
        var count = 0
        if (summary(week, today, slots, logs, goalFor(week, goals)).active) count++
        week = week.minusWeeks(1)
        while (week.plusDays(6).toEpochDay() >= first) {
            if (!summary(week, today, slots, logs, goalFor(week, goals)).active) break
            count++
            week = week.minusWeeks(1)
        }
        return count
    }

    /** Ieri c'era una sessione pianificata e non è stata fatta: oggi bastano 10 minuti. */
    fun yesterdaySkipped(today: LocalDate, slots: List<SlotEntity>, logs: List<SessionLogEntity>): Boolean =
        dayState(today.minusDays(1), today, slots, logs) == DayState.SKIPPED

    /** Settimana da rivedere: quella in corso da venerdì in poi, altrimenti la precedente. */
    fun reviewWeek(today: LocalDate): LocalDate =
        if (today.dayOfWeek >= DayOfWeek.FRIDAY) start(today) else start(today).minusWeeks(1)
}
