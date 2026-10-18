package it.mithril.app.data

import android.content.Context
import java.time.LocalDate

/** Sessione iniziata e non finita: da dove riprendere (usato dal player e dal widget "Primo video"). */
data class Progress(val slotId: Long, val date: Long, val index: Int, val position: Float, val watched: Float, val total: Int)

object SessionProgress {
    private fun prefs(context: Context) = context.getSharedPreferences("session_progress", Context.MODE_PRIVATE)

    fun save(context: Context, p: Progress) = prefs(context).edit()
        .putLong("slot", p.slotId).putLong("date", p.date).putInt("index", p.index)
        .putFloat("position", p.position).putFloat("watched", p.watched).putInt("total", p.total).apply()

    /** Avanzamento valido solo per lo stesso slot e lo stesso giorno. */
    fun get(context: Context, slotId: Long): Progress? {
        val sp = prefs(context)
        if (sp.getLong("slot", -1) != slotId || sp.getLong("date", -1) != LocalDate.now().toEpochDay()) return null
        return Progress(slotId, sp.getLong("date", 0), sp.getInt("index", 0), sp.getFloat("position", 0f), sp.getFloat("watched", 0f), sp.getInt("total", 0))
    }

    fun clear(context: Context) = prefs(context).edit().clear().apply()
}

/** Ultimi passi letti con l'app aperta: il widget li usa quando Health Connect non è leggibile in background. */
object StepsCache {
    private fun prefs(context: Context) = context.getSharedPreferences("steps_cache", Context.MODE_PRIVATE)
    fun save(context: Context, steps: Int) = prefs(context).edit().putLong("date", LocalDate.now().toEpochDay()).putInt("steps", steps).apply()
    fun today(context: Context): Int? {
        val sp = prefs(context)
        return if (sp.getLong("date", -1) == LocalDate.now().toEpochDay()) sp.getInt("steps", 0) else null
    }
}
