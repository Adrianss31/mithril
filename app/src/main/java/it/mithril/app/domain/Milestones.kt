package it.mithril.app.domain

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Micro-tappa automatica da 1 kg verso l'obiettivo finale, così il prossimo traguardo è sempre vicino.
 */
data class WeightMilestone(
    val nextKg: Double,
    val prevKg: Double,
    val remainingKg: Double,
    val progress: Float,
    val etaDays: Int?,
    val milestonesDone: Int,
    val justCrossedKg: Double?,
    val backpackLabel: String?,
)

object Milestones {
    fun forTrend(trend: Trend, today: LocalDate, targetKg: Double, startKg: Double?): WeightMilestone? {
        val t = today.toEpochDay()
        val now = trend.at(t) ?: return null
        val start = startKg ?: now
        val dir = if (targetKg < start) -1.0 else 1.0
        val overallRemaining = (targetKg - now) * dir
        if (overallRemaining <= 0.0) return null

        val nextKg: Double
        val prevKg: Double
        val milestonesDone: Int
        if (dir < 0) {
            nextKg = maxOf(targetKg, ceil(now - 1e-6) - 1.0)
            prevKg = nextKg + 1.0
            milestonesDone = (floor(start) - floor(now)).toInt().coerceAtLeast(0)
        } else {
            nextKg = minOf(targetKg, floor(now + 1e-6) + 1.0)
            prevKg = nextKg - 1.0
            milestonesDone = (floor(now) - floor(start)).toInt().coerceAtLeast(0)
        }

        val span = abs(prevKg - nextKg).coerceAtLeast(0.1)
        val remainingKg = ((nextKg - now) * dir).coerceAtLeast(0.0)
        val progress = (1.0 - remainingKg / span).toFloat().coerceIn(0f, 1f)
        val towards = trend.dailySlope(t)?.let { it * dir }
        val etaDays = towards?.takeIf { it > Trend.STABLE_SLOPE }?.let { ceil(remainingKg / it).toInt().coerceAtMost(365) }

        val yesterday = trend.at(t - 1)
        val justCrossedKg = if (yesterday != null) {
            if (dir < 0 && floor(now) < floor(yesterday)) floor(yesterday)
            else if (dir > 0 && floor(now) > floor(yesterday)) floor(now)
            else null
        } else null

        val doneTotal = ((now - start) * dir).coerceAtLeast(0.0)
        return WeightMilestone(
            nextKg = nextKg,
            prevKg = prevKg,
            remainingKg = remainingKg,
            progress = progress,
            etaDays = etaDays,
            milestonesDone = milestonesDone,
            justCrossedKg = justCrossedKg,
            backpackLabel = backpackEquivalent(doneTotal),
        )
    }

    /** Traduce i chili persi in un oggetto fisico concreto. */
    fun backpackEquivalent(doneKg: Double): String? = when {
        doneKg < 0.5 -> null
        doneKg < 1.2 -> "un pacco di pasta"
        doneKg < 2.2 -> "una bottiglia d'acqua da 1,5 L"
        doneKg < 4.0 -> "uno zaino col portatile"
        doneKg < 6.5 -> "una cassa d'acqua"
        doneKg < 9.0 -> "una grossa spesa settimanale"
        doneKg < 13.0 -> "un bagaglio a mano pieno"
        doneKg < 18.0 -> "una valigia da stiva"
        else -> "due casse d'acqua e mezzo"
    }
}
