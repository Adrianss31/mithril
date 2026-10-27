package it.mithril.app

import it.mithril.app.data.SessionLogEntity
import it.mithril.app.data.SlotEntity
import it.mithril.app.data.Tags
import it.mithril.app.data.VideoEntity
import it.mithril.app.data.YouTube
import it.mithril.app.domain.DayState
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Goal
import it.mithril.app.domain.Stats
import it.mithril.app.domain.StatsPeriod
import it.mithril.app.domain.TimeSuggest
import it.mithril.app.widget.formatSteps
import it.mithril.app.domain.Trend
import it.mithril.app.domain.Week
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DomainTest {
    private val friday = LocalDate.of(2026, 10, 16)

    private fun slot(date: LocalDate, id: Long = date.toEpochDay()) = SlotEntity(id, date.toEpochDay(), 18 * 60 + 30, "Soggiorno", "1,2")
    private fun log(date: LocalDate, mini: Boolean = false) = SessionLogEntity(
        0, null, date.toEpochDay(), if (mini) "MINI" else "FULL", 1f, 6, null, 0, "x", "1", 600,
    )

    @Test fun formatsAreItalian() {
        assertEquals("84,9", Fmt.kg(84.94))
        assertEquals("−0,3", Fmt.signedKg(-0.28))
        assertEquals("+1,2", Fmt.signedKg(1.2))
        assertEquals("5.840", Fmt.thousands(5840))
        assertEquals("ven 16 ott", Fmt.dayShort(friday))
        assertEquals("Venerdì 16", Fmt.dayTitle(friday))
        assertEquals("12 – 18 ottobre", Fmt.weekRange(LocalDate.of(2026, 10, 12)))
        assertEquals("28 settembre – 4 ottobre", Fmt.weekRange(LocalDate.of(2026, 9, 28)))
        assertEquals("metà gennaio", Fmt.eta(LocalDate.of(2027, 1, 15), LocalDate.of(2027, 1, 2)))
        assertEquals("metà novembre 2027", Fmt.eta(LocalDate.of(2027, 11, 15), friday))
        assertEquals("25:12", Fmt.clock(25 * 60 + 12))
        assertEquals("25'", Fmt.minutes(25 * 60 + 12))
    }

    @Test fun trendSmoothsAndInterpolates() {
        val d0 = friday.toEpochDay() - 20
        val t = Trend.of(listOf(d0 to 90.0, d0 + 10 to 89.0, d0 + 20 to 88.0), friday.toEpochDay())
        assertEquals(90.0, t.at(d0)!!, 1e-9)
        // Il trend insegue le pesate ma resta più alto mentre il peso scende.
        val now = t.at(friday.toEpochDay())!!
        assertTrue(now < 90.0 && now > 88.0)
        assertNull(t.at(d0 - 1))
        assertTrue(t.change(friday.toEpochDay(), 7)!! < 0)
    }

    @Test fun goalStatusAndEta() {
        val today = friday.toEpochDay()
        val weights = (0..60).map { (today - 60 + it) to 90.0 - it * 0.06 }
        val trend = Trend.of(weights, today)
        val s = Goal.status(trend, friday, 82.0, 0.25, 90.0)!!
        assertTrue(s.onTrack)
        assertNotNull(s.eta)
        assertTrue(s.progress in 0f..1f)
        val flat = Trend.of((0..30).map { (today - 30 + it) to 85.0 }, today)
        val f = Goal.status(flat, friday, 82.0, 0.25, 85.0)!!
        assertTrue(f.stable); assertNull(f.eta); assertFalse(f.onTrack)
    }

    @Test fun projectionStopsAtGoalOrHorizon() {
        val today = friday.toEpochDay()
        // −0,06 kg al giorno: 3 kg in 50 giorni.
        val trend = Trend.of((0..60).map { (today - 60 + it) to 90.0 - it * 0.06 }, today)
        val now = trend.at(today)!!
        val far = trend.projection(today, 30, 70.0)!!
        assertEquals(30, far.days)
        assertFalse(far.reachesGoal)
        assertEquals(14, far.basisDays)
        assertEquals(now + far.slopePerDay * 30, far.endKg, 1e-9)
        val near = trend.projection(today, 91, now - 1.0)!!
        assertTrue(near.reachesGoal)
        assertTrue(near.days in 1..91)
        assertEquals(now - 1.0, near.endKg, 1e-9)
        // Obiettivo nella direzione opposta: si proietta fino all'orizzonte.
        assertFalse(trend.projection(today, 91, 95.0)!!.reachesGoal)
        val flat = Trend.of((0..30).map { (today - 30 + it) to 85.0 }, today).projection(today, 91, 80.0)!!
        assertTrue(flat.stable); assertEquals(85.0, flat.endKg, 1e-9)
        // Meno di una settimana di trend: niente proiezione.
        assertNull(Trend.of((0..4).map { (today - 4 + it) to 85.0 }, today).projection(today, 91, 80.0))
    }

    @Test fun dayStatesAndStreak() {
        val mon = Week.start(friday)
        assertEquals(LocalDate.of(2026, 10, 12), mon)
        val slots = listOf(slot(mon), slot(mon.plusDays(2)), slot(mon.plusDays(3)), slot(friday), slot(friday.plusDays(2)))
        val logs = listOf(log(mon), log(mon.plusDays(2), mini = true))
        val s = Week.summary(mon, friday, slots, logs, 3)
        assertEquals(DayState.DONE, s.days[0].state)
        assertEquals(DayState.REST, s.days[1].state)
        assertEquals(DayState.MINI, s.days[2].state)
        assertEquals(DayState.SKIPPED, s.days[3].state)
        assertEquals(DayState.PLANNED, s.days[4].state)
        assertTrue(s.days[4].isToday)
        assertEquals(2, s.sessions)
        assertFalse(s.active)
        assertTrue(Week.yesterdaySkipped(friday, slots, logs))

        val prev = mon.minusWeeks(1)
        val older = (0L..2L).map { log(prev.plusDays(it)) } + (0L..2L).map { log(prev.minusWeeks(1).plusDays(it)) }
        assertEquals(2, Week.activeStreak(friday, slots, logs + older, emptyMap()))
        assertEquals(3, Week.activeStreak(friday, slots, logs + older + log(friday), emptyMap()))
    }

    @Test fun filtersAreOrWithinGroupAndAcrossGroups() {
        val v = VideoEntity(1, "abcdefghijk", "Core dolce", "Canale", 600, "", "Forza,Dolce,Core")
        assertTrue(Tags.matches(v, setOf("Forza", "Cardio"), ""))
        assertTrue(Tags.matches(v, setOf("Forza", "≤15'"), ""))
        assertFalse(Tags.matches(v, setOf("Forza", "30'+"), ""))
        assertFalse(Tags.matches(v, setOf("Cardio"), ""))
        assertTrue(Tags.matches(v, emptySet(), "canale"))
        assertFalse(Tags.matches(v, emptySet(), "yoga"))
    }

    @Test fun parsesSharedLinks() {
        assertEquals("ml6cT4AZdqI", YouTube.parseId("Guarda \"30-Minute HIIT\" su YouTube https://youtu.be/ml6cT4AZdqI?si=abc"))
        assertEquals("ml6cT4AZdqI", YouTube.parseId("https://www.youtube.com/watch?v=ml6cT4AZdqI&t=10"))
        assertEquals("ml6cT4AZdqI", YouTube.parseId("https://youtube.com/shorts/ml6cT4AZdqI"))
        assertNull(YouTube.parseId("nessun link qui"))
    }

    @Test fun suggestedTimesFollowUsage() {
        assertEquals(TimeSuggest.DEFAULTS, TimeSuggest.suggest(emptyList(), friday))
        val custom = 18 * 60 + 45
        // Poche pianificazioni: l'orario personalizzato compare accanto ai predefiniti.
        val few = (1..3).map { SlotEntity(it.toLong(), friday.toEpochDay() - it * 7, custom, "Soggiorno", "1") }
        assertTrue(custom in TimeSuggest.suggest(few, friday))
        // Tante pianificazioni sempre agli stessi orari: i predefiniti mai scelti spariscono.
        val many = (1..40).map { SlotEntity(it.toLong(), friday.toEpochDay() - it, if (it % 2 == 0) custom else 7 * 60, "Soggiorno", "1") }
        assertEquals(listOf(7 * 60, custom), TimeSuggest.suggest(many, friday))
    }

    @Test fun stepsAreShortInWidgets() {
        assertEquals("7,2k", formatSteps(7210))
        assertEquals("850", formatSteps(850))
        assertEquals("—", formatSteps(null))
    }

    @Test fun statsSummarizeThePeriod() {
        val mon = Week.start(friday) // 12 ottobre
        val slots = listOf(slot(mon), slot(mon.plusDays(1)), slot(mon.plusDays(3)))
        val logs = listOf(log(mon), log(mon.plusDays(2), mini = true), log(mon.plusDays(4)), log(mon.minusDays(7)), log(mon.minusDays(5)), log(mon.minusDays(3)))
        // Martedì saltato e recuperato mercoledì con una mini; giovedì saltato e recuperato venerdì.
        val steps = mapOf(mon.toEpochDay() to 9000, mon.plusDays(1).toEpochDay() to 3000, mon.plusDays(2).toEpochDay() to 6000)
        val weights = (0..20).map { friday.minusDays(it.toLong()).toEpochDay() to 80.0 + it * 0.1 }
        val from = Stats.from(StatsPeriod.W4, friday, logs, weights, slots)
        assertEquals(friday.minusDays(27), from)
        val s = Stats.summary(from, friday, slots, logs, emptyMap(), weights, steps, 7000)
        assertEquals(5, s.sessions); assertEquals(1, s.mini)
        assertEquals(60, s.minutes) // 6 sessioni da 10'
        assertEquals(2, s.activeWeeks)
        assertEquals(2 to 2, s.streak to s.bestStreak)
        assertEquals(6000, s.stepsAvg)
        assertEquals(1, s.stepsGoalDays)
        assertEquals(7500, s.stepsTraining); assertEquals(3000, s.stepsRest)
        assertEquals(2, s.skipped); assertEquals(2, s.recovered)
        assertTrue(s.trendChange!! < 0)
        assertEquals(listOf(2, 0, 2, 0, 2, 0, 0), s.weekday)
        assertEquals(listOf(1L to 6), s.topVideos)
        assertEquals(listOf(9000, 6000, 6000), Stats.rollingSteps(listOf(mon.toEpochDay(), mon.plusDays(1).toEpochDay(), mon.plusDays(2).toEpochDay()), steps))
    }

    @Test fun recentChangeWorksOnAYoungTrend() {
        val t = friday.toEpochDay()
        val young = Trend.of((0..5).map { t - it to 86.0 + it * 0.3 }, t) // pesate da 5 giorni
        assertNull(young.change(t, 7))
        val (kg, days) = young.recentChange(t)!!
        assertEquals(5, days); assertTrue(kg < 0)
        assertEquals(7, Trend.of((0..20).map { t - it to 86.0 }, t).recentChange(t)!!.second)
        assertNull(Trend.of(listOf(t to 86.0), t).recentChange(t))
    }
}
