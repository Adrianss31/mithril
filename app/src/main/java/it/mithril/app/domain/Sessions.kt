package it.mithril.app.domain

import java.time.LocalDate
import it.mithril.app.data.SessionLogEntity
import it.mithril.app.data.SlotEntity
import it.mithril.app.data.VideoEntity

object Sessions {
    const val MINI_MAX_SEC = 10 * 60 + 30
    const val DONE_RATIO = 0.8f

    fun videosOf(ids: List<Long>, all: List<VideoEntity>): List<VideoEntity> {
        val byId = all.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    /** Versione mini: quella indicata dal primo video che ne ha una, altrimenti il video più breve (≤ 10') della libreria. */
    fun miniFor(sessionVideos: List<VideoEntity>, all: List<VideoEntity>): VideoEntity? {
        val byId = all.associateBy { it.id }
        sessionVideos.firstNotNullOfOrNull { v -> v.miniVideoId?.let { byId[it] } }?.let { return it }
        return all.filter { (it.durationSec ?: Int.MAX_VALUE) <= MINI_MAX_SEC }.minByOrNull { it.durationSec ?: Int.MAX_VALUE }
    }

    fun totalSec(videos: List<VideoEntity>): Int = videos.sumOf { it.durationSec ?: 0 }

    /** "Full body 25' senza attrezzi" → "Full body" (per notifiche e widget). */
    fun shortTitle(title: String): String {
        val words = title.split(' ').filter { it.isNotBlank() }
        val cut = words.indexOfFirst { w -> w.any { it.isDigit() } || w == "-" || w == "|" || w == "//" }.let { if (it <= 0) words.size else it }
        return words.take(minOf(cut, 3)).joinToString(" ").trimEnd(',', ':', '-').ifBlank { title.take(24) }
    }

    fun title(videos: List<VideoEntity>, short: Boolean = false): String =
        videos.joinToString(" + ") { if (short) shortTitle(it.title) else it.title }.ifBlank { "Sessione" }

    /** Prossima sessione non ancora fatta: oggi (se l'ora non è passata da troppo) o nei giorni seguenti. */
    fun next(slots: List<SlotEntity>, logs: List<SessionLogEntity>, today: LocalDate, nowMin: Int, includeToday: Boolean = true): SlotEntity? {
        val t = today.toEpochDay()
        val doneSlots = logs.mapNotNull { it.slotId }.toSet()
        val loggedToday = logs.any { it.date == t }
        return slots.filter { it.id !in doneSlots }
            .filter { s ->
                when {
                    s.date > t -> true
                    s.date == t -> includeToday && !loggedToday
                    else -> false
                }
            }
            .sortedWith(compareBy({ it.date }, { it.timeMin }))
            .firstOrNull()
    }

    fun todaySlots(slots: List<SlotEntity>, today: LocalDate) = slots.filter { it.date == today.toEpochDay() }.sortedBy { it.timeMin }
}
