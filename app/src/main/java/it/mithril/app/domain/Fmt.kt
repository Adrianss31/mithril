package it.mithril.app.domain

import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Formati italiani: virgola decimale, punto per le migliaia, meno tipografico, date brevi. */
object Fmt {
    val IT: Locale = Locale.ITALIAN
    const val MINUS = "−"

    fun kg(v: Double): String = String.format(Locale.ITALY, "%.1f", v).replace("-", MINUS)
    fun signedKg(v: Double): String {
        val r = (v * 10).roundToInt() / 10.0
        return when {
            r < 0 -> MINUS + String.format(Locale.ITALY, "%.1f", abs(r))
            r > 0 -> "+" + String.format(Locale.ITALY, "%.1f", r)
            else -> "0,0"
        }
    }
    fun thousands(n: Int): String = NumberFormat.getIntegerInstance(Locale.ITALY).format(n)

    fun dayLetter(d: DayOfWeek): String = when (d) {
        DayOfWeek.MONDAY -> "L"; DayOfWeek.TUESDAY -> "M"; DayOfWeek.WEDNESDAY -> "M"
        DayOfWeek.THURSDAY -> "G"; DayOfWeek.FRIDAY -> "V"; DayOfWeek.SATURDAY -> "S"; DayOfWeek.SUNDAY -> "D"
    }
    fun dayName(d: DayOfWeek): String = d.getDisplayName(TextStyle.FULL, IT).replaceFirstChar { it.uppercase() }
    fun dayShortName(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, IT).take(3).replaceFirstChar { it.uppercase() }
    fun month(date: LocalDate): String = date.month.getDisplayName(TextStyle.FULL_STANDALONE, IT).lowercase(IT)
    fun monthShort(date: LocalDate): String = date.month.getDisplayName(TextStyle.SHORT, IT).take(3).lowercase(IT)

    /** "ven 16 ott" */
    fun dayShort(date: LocalDate): String =
        "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, IT).take(3).lowercase(IT)} ${date.dayOfMonth} ${monthShort(date)}"
    /** "Venerdì 16" */
    fun dayTitle(date: LocalDate): String = "${dayName(date.dayOfWeek)} ${date.dayOfMonth}"
    /** "Sabato 17 ottobre" */
    fun dayLong(date: LocalDate): String = "${dayName(date.dayOfWeek)} ${date.dayOfMonth} ${month(date)}"
    /** "16 ott" */
    fun dateShort(date: LocalDate): String = "${date.dayOfMonth} ${monthShort(date)}"
    /** "16 ott", con l'anno se non è quello di [today]: "16 ott 2027" */
    fun dateShort(date: LocalDate, today: LocalDate): String = if (date.year == today.year) dateShort(date) else "${dateShort(date)} ${date.year}"

    /** "12 – 18 ottobre" oppure "29 settembre – 5 ottobre" */
    fun weekRange(start: LocalDate): String {
        val end = start.plusDays(6)
        return if (start.month == end.month) "${start.dayOfMonth} – ${end.dayOfMonth} ${month(end)}"
        else "${start.dayOfMonth} ${month(start)} – ${end.dayOfMonth} ${month(end)}"
    }

    fun time(min: Int): String = "%02d:%02d".format(min / 60, min % 60)
    /** "25'" */
    fun minutes(sec: Int?): String = if (sec == null || sec <= 0) "–'" else "${((sec + 30) / 60).coerceAtLeast(1)}'"
    /** "25:12" */
    fun clock(sec: Int?): String {
        if (sec == null || sec <= 0) return "–:––"
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** "inizio/metà/fine gennaio", con l'anno se non è quello di [today]: "metà novembre 2027" */
    fun eta(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val part = when {
            date.dayOfMonth <= 10 -> "inizio"
            date.dayOfMonth <= 20 -> "metà"
            else -> "fine"
        }
        return if (date.year == today.year) "$part ${month(date)}" else "$part ${month(date)} ${date.year}"
    }

    fun overlineFor(min: Int): String = when {
        min < 12 * 60 -> "STAMATTINA"
        min < 18 * 60 -> "OGGI"
        else -> "STASERA"
    }
}
