package it.mithril.app.domain

import it.mithril.app.data.SlotEntity
import java.time.LocalDate
import kotlin.math.pow

/**
 * Orari proposti nel foglio "Nuovo slot": quelli usati di recente salgono, quelli predefiniti
 * mai scelti perdono peso a ogni slot pianificato e alla fine spariscono.
 */
object TimeSuggest {
    val DEFAULTS = listOf(7 * 60, 7 * 60 + 30, 12 * 60 + 30, 18 * 60 + 30, 19 * 60 + 30, 21 * 60)
    private const val WEEKLY_DECAY = 0.85
    private const val PRIOR_DECAY = 0.9
    private const val MIN_SCORE = 0.05

    fun suggest(slots: List<SlotEntity>, today: LocalDate, max: Int = 6): List<Int> {
        val usage = HashMap<Int, Double>()
        for (s in slots) {
            val weeks = (today.toEpochDay() - s.date).coerceAtLeast(0) / 7.0
            usage[s.timeMin] = (usage[s.timeMin] ?: 0.0) + WEEKLY_DECAY.pow(weeks)
        }
        val prior = PRIOR_DECAY.pow(slots.size)
        val scores = (DEFAULTS + usage.keys).distinct().associateWith { (usage[it] ?: 0.0) + if (it in DEFAULTS) prior else 0.0 }
        val picked = scores.filter { it.value >= MIN_SCORE }.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Double>> { it.value }.thenBy { it.key })
            .take(max).map { it.key }.sorted()
        return picked.ifEmpty { DEFAULTS }
    }
}
