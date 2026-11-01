package it.mithril.app.domain

/** Inizio e fine di un video (per saltare intro e outro): lettura di "1:30" e controlli. */
object Trim {
    /** Il tratto più corto che si può tenere. */
    const val MIN_SEC = 30

    /** "1:30", "1.30", "1:02:30" o solo secondi ("90"); null se vuoto o non valido. */
    fun parse(text: String): Int? {
        val parts = text.trim().split(Regex("[:.,' ]+")).filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.size > 3) return null
        val n = parts.map { it.toIntOrNull() ?: return null }
        if (n.size > 1 && n.drop(1).any { it >= 60 }) return null
        return n.fold(0) { acc, v -> acc * 60 + v }
    }

    /** Messaggio da mostrare se inizio e fine non vanno bene, altrimenti null. */
    fun error(start: Int?, end: Int?, startText: String, endText: String, durationSec: Int?): String? {
        val dur = durationSec?.takeIf { it > 0 }
        return when {
            startText.isNotBlank() && start == null -> "Inizio non valido: scrivi minuti e secondi, come 0:45"
            endText.isNotBlank() && end == null -> "Fine non valida: scrivi minuti e secondi, come 28:10"
            dur != null && (start ?: 0) >= dur -> "L'inizio va oltre la durata del video (${Fmt.clock(dur)})"
            dur != null && end != null && end > dur -> "La fine va oltre la durata del video (${Fmt.clock(dur)})"
            end != null && end - (start ?: 0) < MIN_SEC -> "Tra inizio e fine servono almeno $MIN_SEC secondi"
            else -> null
        }
    }

    /** Una fine uguale alla durata vuol dire "fino alla fine". */
    fun end(end: Int?, durationSec: Int?): Int? = end?.takeUnless { durationSec != null && durationSec > 0 && it >= durationSec }
}
