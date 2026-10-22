package it.mithril.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import it.mithril.app.data.SessionLogEntity
import it.mithril.app.data.SlotEntity

enum class StatsPeriod(val label: String, val days: Int?) {
    W4("4 sett.", 28), M3("3 mesi", 91), Y1("Anno", 365), ALL("Tutto", null)
}

data class WeekBar(val start: LocalDate, val full: Int, val mini: Int, val minutes: Int, val goal: Int) {
    val sessions: Int get() = full + mini
    val active: Boolean get() = sessions >= goal
}

data class StatsSummary(
    val from: LocalDate,
    val to: LocalDate,
    val sessions: Int,
    val mini: Int,
    val minutes: Int,
    val trainingDays: Int,
    val weeks: List<WeekBar>,
    val streak: Int,
    val bestStreak: Int,
    val stepsAvg: Int?,
    val stepsBest: Pair<LocalDate, Int>?,
    val stepsGoalDays: Int,
    val stepsDays: Int,
    /** Media dei passi nei giorni con e senza allenamento. */
    val stepsTraining: Int?,
    val stepsRest: Int?,
    val trendStart: Double?,
    val trendEnd: Double?,
    val weighIns: Int,
    /** Sessioni per giorno della settimana, lunedì per primo. */
    val weekday: List<Int>,
    /** Ora d'inizio tipica (mediana, minuti dal mezzanotte). */
    val typicalStartMin: Int?,
    val fatigueAvg: Double?,
    /** Sessioni pianificate saltate, e quante volte il giorno dopo ti sei allenato lo stesso. */
    val skipped: Int,
    val recovered: Int,
    val topVideos: List<Pair<Long, Int>>,
) {
    val activeWeeks: Int get() = weeks.count { it.active }
    val trendChange: Double? get() = if (trendStart != null && trendEnd != null) trendEnd - trendStart else null
}

/** Numeri del pannello Statistiche: tutto calcolato dai dati locali, nessuna stima. */
object Stats {
    /** Minuti effettivamente guardati di una sessione. */
    fun minutes(log: SessionLogEntity): Int = (log.durationSec * log.watchedRatio.coerceIn(0f, 1f) / 60f).roundToInt()

    /** Primo giorno del periodo: per "Tutto" il primo dato registrato. */
    fun from(period: StatsPeriod, today: LocalDate, logs: List<SessionLogEntity>, weights: List<Pair<Long, Double>>, slots: List<SlotEntity>): LocalDate {
        period.days?.let { return today.minusDays(it - 1L) }
        val first = listOfNotNull(logs.minOfOrNull { it.date }, weights.minOfOrNull { it.first }, slots.minOfOrNull { it.date }).minOrNull()
        return first?.let { minOf(LocalDate.ofEpochDay(it), today.minusDays(27)) } ?: today.minusDays(27)
    }

    /** Serie attuale e record di settimane attive (sull'intera storia, non solo sul periodo). */
    fun streaks(today: LocalDate, slots: List<SlotEntity>, logs: List<SessionLogEntity>, goals: Map<Long, Int>): Pair<Int, Int> {
        val first = logs.minOfOrNull { it.date } ?: return 0 to 0
        val current = Week.start(today)
        var w = Week.start(LocalDate.ofEpochDay(first))
        var run = 0
        var best = 0
        while (!w.isAfter(current)) {
            if (Week.summary(w, today, slots, logs, Week.goalFor(w, goals)).active) { run++; best = maxOf(best, run) }
            else if (w != current) run = 0
            w = w.plusWeeks(1)
        }
        return Week.activeStreak(today, slots, logs, goals) to best
    }

    fun summary(
        from: LocalDate,
        today: LocalDate,
        slots: List<SlotEntity>,
        logs: List<SessionLogEntity>,
        goals: Map<Long, Int>,
        weights: List<Pair<Long, Double>>,
        steps: Map<Long, Int>,
        stepsGoal: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): StatsSummary {
        val f = from.toEpochDay(); val t = today.toEpochDay()
        val inRange = logs.filter { it.date in f..t }
        val logDays = inRange.map { it.date }.toSet()

        val weeks = generateSequence(Week.start(from)) { it.plusWeeks(1) }.takeWhile { !it.isAfter(today) }.map { w ->
            val ws = w.toEpochDay()
            val wl = logs.filter { it.date in ws..ws + 6 }
            WeekBar(w, wl.count { !it.isMini }, wl.count { it.isMini }, wl.sumOf { minutes(it) }, Week.goalFor(w, goals))
        }.toList()
        val (streak, best) = streaks(today, slots, logs, goals)

        val stepDays = (f..t).mapNotNull { d -> steps[d]?.takeIf { it > 0 }?.let { d to it } }
        val training = stepDays.filter { it.first in logDays }.map { it.second }
        val rest = stepDays.filter { it.first !in logDays }.map { it.second }

        val enough = weights.size >= Trend.MIN_WEIGH_INS
        val trend = if (enough) Trend.of(weights, t) else null
        val trendStart = trend?.let { tr -> tr.at(maxOf(f, tr.first)) }
        val trendEnd = trend?.at(t)

        val weekday = MutableList(7) { 0 }
        inRange.forEach { weekday[LocalDate.ofEpochDay(it.date).dayOfWeek.value - 1]++ }
        val starts = inRange.map { l ->
            val end = Instant.ofEpochMilli(l.completedAt).atZone(zone).toLocalTime().toSecondOfDay() / 60
            Math.floorMod(end - minutes(l), 24 * 60)
        }.sorted()

        // "Mai due volte di fila": dopo un giorno saltato, ti sei allenato il giorno dopo?
        val skippedDays = (f until t).filter { d ->
            Week.dayState(LocalDate.ofEpochDay(d), today, slots, logs) == DayState.SKIPPED && (d + 1 < t || (d + 1) in logDays)
        }
        val allLogDays = logs.map { it.date }.toSet()

        val counts = HashMap<Long, Int>()
        inRange.forEach { l -> l.videoIds.split(',').mapNotNull { it.trim().toLongOrNull() }.forEach { counts[it] = (counts[it] ?: 0) + 1 } }

        return StatsSummary(
            from = from, to = today,
            sessions = inRange.count { !it.isMini },
            mini = inRange.count { it.isMini },
            minutes = inRange.sumOf { minutes(it) },
            trainingDays = logDays.size,
            weeks = weeks,
            streak = streak, bestStreak = best,
            stepsAvg = stepDays.takeIf { it.isNotEmpty() }?.let { s -> s.sumOf { it.second } / s.size },
            stepsBest = stepDays.maxByOrNull { it.second }?.let { LocalDate.ofEpochDay(it.first) to it.second },
            stepsGoalDays = stepDays.count { it.second >= stepsGoal },
            stepsDays = stepDays.size,
            stepsTraining = training.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size },
            stepsRest = rest.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size },
            trendStart = trendStart, trendEnd = trendEnd,
            weighIns = weights.count { it.first in f..t },
            weekday = weekday,
            typicalStartMin = starts.takeIf { it.isNotEmpty() }?.get(starts.size / 2),
            fatigueAvg = inRange.mapNotNull { it.fatigue }.takeIf { it.isNotEmpty() }?.average(),
            skipped = skippedDays.size,
            recovered = skippedDays.count { (it + 1) in allLogDays },
            topVideos = counts.entries.sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key }).take(5).map { it.key to it.value },
        )
    }

    /** Media mobile a 7 giorni dei passi (solo sui giorni con dati). */
    fun rollingSteps(days: List<Long>, steps: Map<Long, Int>): List<Int?> = days.map { d ->
        val window = (d - 6..d).mapNotNull { steps[it]?.takeIf { s -> s > 0 } }
        if (window.isEmpty()) null else window.sum() / window.size
    }
}
