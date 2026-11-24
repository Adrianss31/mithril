package it.mithril.app.domain

import it.mithril.app.data.ReviewAnswerEntity
import java.time.DayOfWeek
import java.time.LocalDate

data class ObstacleTip(
    val title: String,
    val body: String,
    val dayOfWeek: DayOfWeek? = null,
)

/** Trasforma le risposte della Revisione settimanale in suggerimenti pratici quando si pianifica. */
object ObstacleInsights {
    fun forPlanner(reviews: List<ReviewAnswerEntity>, today: LocalDate): ObstacleTip? {
        val cutoff = today.minusWeeks(8).toEpochDay()
        val recent = reviews.filter { it.weekStart >= cutoff && (!it.obstacle.isNullOrBlank() || it.skippedDay != null) }
            .sortedByDescending { it.weekStart }
        if (recent.isEmpty()) return null

        val byDay = recent.mapNotNull { r -> r.skippedDay?.let { LocalDate.ofEpochDay(it).dayOfWeek to r.obstacle } }
            .groupBy { it.first }
        val topDayEntry = byDay.entries.filter { it.value.size >= 2 }.maxByOrNull { it.value.size }
        if (topDayEntry != null) {
            val day = topDayEntry.key
            val dayLabel = Fmt.dayName(day)
            val mainObs = topDayEntry.value.mapNotNull { it.second }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            val body = when (mainObs) {
                "Stanchezza" -> "Spesso di ${dayLabel.lowercase()} sei stanco: prova a mettere una sessione breve o un altro giorno."
                "Orario sbagliato" -> "Di ${dayLabel.lowercase()} l'orario tende a saltare: prova a spostarlo nello step successivo."
                else -> "Nelle ultime settimane il ${dayLabel.lowercase()} è saltato ${topDayEntry.value.size} volte: valuta se tenerlo leggero."
            }
            return ObstacleTip("Nota dalle tue revisioni", body, day)
        }

        val obsCutoff = today.minusWeeks(6).toEpochDay()
        val topObstacle = recent.filter { it.weekStart >= obsCutoff }.mapNotNull { it.obstacle }
            .groupingBy { it }.eachCount().entries.filter { it.value >= 2 }.maxByOrNull { it.value }?.key
            ?: return null

        val body = when (topObstacle) {
            "Stanchezza" -> "Ultimamente la stanchezza ti frena spesso: pianifica un obiettivo realistico e scegli video brevi nei giorni pieni."
            "Orario sbagliato" -> "Più volte l'orario non ha funzionato: prova un momento diverso della giornata nello step delle intenzioni."
            "Imprevisto" -> "Quando la settimana ha imprevisti, aiuta lasciare un giorno cuscinetto senza allenamenti."
            "Voglia" -> "Quando cala la voglia, scegli i tuoi video preferiti o parti dall'idea della versione mini."
            else -> "Tieni il piano leggero e facile da rispettare anche nei giorni storti."
        }
        return ObstacleTip("Dalle ultime revisioni", body, null)
    }

    fun dayHint(reviews: List<ReviewAnswerEntity>, day: DayOfWeek, today: LocalDate): String? {
        val cutoff = today.minusWeeks(8).toEpochDay()
        val matching = reviews.filter { it.weekStart >= cutoff && it.skippedDay != null && LocalDate.ofEpochDay(it.skippedDay).dayOfWeek == day }
        if (matching.size < 2) return null
        val mainObs = matching.mapNotNull { it.obstacle }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return when (mainObs) {
            "Stanchezza" -> "Spesso salta per stanchezza"
            "Orario sbagliato" -> "Spesso l'orario qui salta"
            else -> "Saltato ${matching.size} volte di recente"
        }
    }
}
