package it.mithril.app.data

/**
 * Etichette dei video, divise in gruppi. Nei filtri della Libreria vale OR dentro lo stesso
 * gruppo e AND tra gruppi diversi; la durata è un gruppo calcolato, non un'etichetta salvata.
 */
object Tags {
    val CATEGORY = listOf("Forza", "Cardio", "Mobilità", "Stretching")
    val INTENSITY = listOf("Dolce", "Media", "Intensa")
    val ZONE = listOf("Core", "Gambe", "Braccia", "Total body")
    val DURATION = listOf("≤15'", "15–30'", "30'+")

    /** Ordine delle etichette nel foglio "Aggiungi video". */
    val EDITABLE = CATEGORY + INTENSITY + ZONE
    /** Ordine dei chip filtro in Libreria. */
    val FILTERS = CATEGORY + DURATION + INTENSITY + ZONE

    fun parse(raw: String): List<String> = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    fun join(tags: Collection<String>): String = EDITABLE.filter { it in tags }.joinToString(",")

    fun group(tag: String): Int = when (tag) {
        in CATEGORY -> 0
        in DURATION -> 1
        in INTENSITY -> 2
        else -> 3
    }

    fun durationBucket(sec: Int?): String? = when {
        sec == null || sec <= 0 -> null
        sec <= 15 * 60 -> DURATION[0]
        sec <= 30 * 60 -> DURATION[1]
        else -> DURATION[2]
    }

    /** "tipo" mostrato sotto i titoli: la prima categoria, oppure "Esercizi". */
    fun kind(tags: List<String>): String = tags.firstOrNull { it in CATEGORY } ?: "Esercizi"

    fun matches(video: VideoEntity, filters: Set<String>, query: String): Boolean {
        val q = query.trim()
        if (q.isNotEmpty() && !video.title.contains(q, true) && !video.channel.contains(q, true)) return false
        if (filters.isEmpty()) return true
        val own = video.tagList.toMutableSet()
        durationBucket(video.durationSec)?.let { own += it }
        return filters.groupBy { group(it) }.all { (_, wanted) -> wanted.any { it in own } }
    }

    /** Etichette suggerite dal titolo (pre-selezione nel foglio "Aggiungi video"). */
    fun suggest(title: String, durationSec: Int?): Set<String> {
        val t = title.lowercase()
        val out = linkedSetOf<String>()
        fun has(vararg words: String) = words.any { t.contains(it) }
        if (has("stretch", "allungament")) out += "Stretching"
        if (has("mobilit", "yoga", "pilates", "mobility")) out += "Mobilità"
        if (has("hiit", "cardio", "tabata", "brucia", "aerob", "jump", "corsa")) out += "Cardio"
        if (has("forza", "strength", "manubri", "tonific", "pesi", "squat", "dumbbell", "kettlebell")) out += "Forza"
        if (has("dolce", "principianti", "beginner", "facile", "leggero", "low impact", "relax")) out += "Dolce"
        if (has("intens", "advanced", "avanzat", "hard", "estremo")) out += "Intensa"
        if (has("core", "addom", "abs", "pancia", "plank")) out += "Core"
        if (has("gambe", "glute", "legs", "leg ", "squat", "lower body")) out += "Gambe"
        if (has("braccia", "arms", "upper body", "spalle", "petto")) out += "Braccia"
        if (has("total body", "full body", "corpo intero", "total-body", "full-body")) out += "Total body"
        if (out.none { it in CATEGORY }) out += if ((durationSec ?: 0) in 1..900 && out.contains("Dolce")) "Mobilità" else "Forza"
        if (out.none { it in INTENSITY }) out += "Media"
        return out
    }
}
