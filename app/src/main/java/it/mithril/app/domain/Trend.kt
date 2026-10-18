package it.mithril.app.domain

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Trend del peso stile Hacker's Diet: media mobile esponenziale giornaliera (α = 0,1) sulle pesate,
 * con i giorni senza pesata interpolati linearmente. Dopo l'ultima pesata il trend resta fermo.
 */
class Trend private constructor(
    val first: Long,
    private val values: DoubleArray,
    val weighIns: List<Pair<Long, Double>>,
) {
    val last: Long get() = first + values.size - 1

    /** Trend al giorno [day] (epochDay); null prima della prima pesata. */
    fun at(day: Long): Double? = when {
        values.isEmpty() || day < first -> null
        day > last -> values.last()
        else -> values[(day - first).toInt()]
    }

    fun change(today: Long, days: Int): Double? {
        val now = at(today) ?: return null
        val before = at(today - days) ?: return null
        if (today - days < first) return null
        return now - before
    }

    /** Variazione giornaliera media del trend (negativa = cala), sulle ultime due settimane se possibile. */
    fun dailySlope(today: Long): Double? {
        val span = if (today - 14 >= first) 14 else if (today - 7 >= first) 7 else return null
        val now = at(today) ?: return null
        val before = at(today - span) ?: return null
        return (now - before) / span
    }

    companion object {
        const val ALPHA = 0.1
        const val MIN_WEIGH_INS = 3

        fun of(weights: List<Pair<Long, Double>>, today: Long): Trend {
            val sorted = weights.sortedBy { it.first }
            if (sorted.isEmpty()) return Trend(today, DoubleArray(0), emptyList())
            val first = sorted.first().first
            val end = maxOf(today, sorted.last().first)
            val out = DoubleArray((end - first + 1).toInt())
            var t = sorted.first().second
            var k = 0
            for (i in out.indices) {
                val day = first + i
                while (k + 1 < sorted.size && sorted[k + 1].first <= day) k++
                val w: Double? = when {
                    sorted[k].first == day -> sorted[k].second
                    k + 1 < sorted.size -> {
                        val (d0, w0) = sorted[k]; val (d1, w1) = sorted[k + 1]
                        w0 + (w1 - w0) * (day - d0).toDouble() / (d1 - d0)
                    }
                    else -> null
                }
                if (w != null) t += ALPHA * (w - t)
                out[i] = t
            }
            return Trend(first, out, sorted)
        }
    }
}

/** Obiettivo di peso e stato rispetto al piano. */
data class GoalStatus(
    val onTrack: Boolean,
    val progress: Float,
    val remainingKg: Double,
    val eta: LocalDate?,
    val stable: Boolean,
)

object Goal {
    /**
     * [weeklyRate] è positivo (kg a settimana verso l'obiettivo). Il ritmo è "in linea" se il trend
     * si avvicina all'obiettivo almeno a quella velocità. L'ETA esiste solo se il trend va nella direzione giusta.
     */
    fun status(trend: Trend, today: LocalDate, targetKg: Double, weeklyRate: Double, startKg: Double?): GoalStatus? {
        val t = today.toEpochDay()
        val now = trend.at(t) ?: return null
        val dir = if (targetKg < (startKg ?: now)) -1.0 else 1.0
        val remaining = (targetKg - now) * dir // > 0 se manca ancora strada
        val slope = trend.dailySlope(t)
        val towards = slope?.let { it * dir } ?: 0.0 // kg/giorno verso l'obiettivo
        val weekly = trend.change(t, 7)?.let { it * dir } ?: (towards * 7)
        val onTrack = remaining <= 0 || weekly >= weeklyRate - 1e-9
        val stable = towards <= 0.002
        val eta = if (remaining > 0 && !stable) today.plusDays(ceil(remaining / towards).toLong().coerceAtMost(3650)) else null
        val start = startKg ?: now
        val total = abs(targetKg - start)
        val progress = if (total < 1e-6) 1f else ((start - now) * -dir / total).toFloat().coerceIn(0f, 1f)
        return GoalStatus(onTrack, progress, remaining.coerceAtLeast(0.0), eta, stable)
    }
}
