package it.mithril.app

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import it.mithril.app.data.Repo
import it.mithril.app.data.SessionLogEntity
import it.mithril.app.data.SlotEntity
import it.mithril.app.data.VideoEntity
import it.mithril.app.data.WeightEntity
import it.mithril.app.data.WeekPlanEntity
import it.mithril.app.domain.Week
import it.mithril.app.ui.AppRoot
import it.mithril.app.ui.Dest
import it.mithril.app.ui.Navigator
import it.mithril.app.ui.SessionRequest
import it.mithril.app.ui.SheetKind
import it.mithril.app.ui.Tab
import it.mithril.app.ui.theme.MithrilTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.math.sin

/**
 * Fotografie della UI con dati sintetici: non verificano nulla, servono a guardare il design
 * senza telefono. I PNG finiscono in build/reports/screenshots (workflow "Schermate UI").
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
abstract class ShotBase {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = File("build/reports/screenshots").apply { mkdirs() }

    protected fun seed(empty: Boolean = false) = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val repo = Repo.get(ctx)
        if (empty) return@runBlocking
        val dao = repo.dao
        val today = LocalDate.now()
        val mon = Week.start(today)
        val v = listOf(
            VideoEntity(0, "aaaaaaaaaa1", "Full body 25' senza attrezzi", "Casa in Movimento", 25 * 60 + 12, "", "Forza,Media,Total body", timesDone = 7),
            VideoEntity(0, "aaaaaaaaaa2", "Stretching serale 10'", "Corpo Lento", 10 * 60 + 30, "", "Stretching,Dolce", timesDone = 4),
            VideoEntity(0, "aaaaaaaaaa3", "Mobilità anche 15'", "Corpo Lento", 15 * 60 + 40, "", "Mobilità,Dolce,Total body", timesDone = 5),
            VideoEntity(0, "aaaaaaaaaa4", "Upper body manubri 30'", "Ferro Gentile", 31 * 60 + 5, "", "Forza,Media,Braccia", timesDone = 3, embeddable = false),
            VideoEntity(0, "aaaaaaaaaa5", "HIIT dolce 20'", "Fiato Corto", 20 * 60 + 22, "", "Cardio,Media,Total body", timesDone = 4),
            VideoEntity(0, "aaaaaaaaaa6", "Core 10'", "Dieci Minuti", 10 * 60 + 4, "", "Forza,Media,Core", timesDone = 9),
        ).map { dao.insertVideo(it) }
        val first = dao.video(v[0])!!
        dao.updateVideo(first.copy(miniVideoId = v[5]))
        fun slot(d: LocalDate, vararg ids: Long) = SlotEntity(0, d.toEpochDay(), 18 * 60 + 30, "Soggiorno", ids.joinToString(","))
        val days = (0L..6L).map { mon.plusDays(it) }
        dao.upsertSlot(slot(days[0], v[0], v[1]))
        dao.upsertSlot(slot(days[2], v[2]))
        if (today != days[4]) dao.upsertSlot(slot(today, v[0], v[1]))
        dao.upsertSlot(slot(days[4], v[0], v[1]))
        dao.upsertSlot(slot(mon.plusDays(8), v[4], v[1]))
        fun log(d: LocalDate, mini: Boolean) = SessionLogEntity(0, null, d.toEpochDay(), if (mini) "MINI" else "FULL", 1f, 6, null, 0L, "Full body", "${v[0]}", 2100)
        if (days[0].isBefore(today)) dao.insertLog(log(days[0], false))
        if (days[2].isBefore(today)) dao.insertLog(log(days[2], true))
        for (w in 1..6) for (k in listOf(0L, 2L, 4L)) dao.insertLog(log(mon.minusWeeks(w.toLong()).plusDays(k), false))
        dao.upsertWeekPlan(WeekPlanEntity(mon.toEpochDay(), 3))
        for (i in 0..90) {
            val d = today.minusDays((90 - i).toLong())
            if (i % 3 == 1) continue
            val kg = 87.0 - i * 0.028 + sin(i * 1.7) * 0.45
            dao.upsertWeight(WeightEntity(d.toEpochDay(), Math.round(kg * 10) / 10.0))
        }
        repo.settings.setGoal(82.0, 87.0, today.minusDays(90).toEpochDay())
        // Aspetta che i flussi di Room arrivino.
        repeat(100) {
            if (repo.videos.value?.size == v.size && repo.weights.value?.isNotEmpty() == true && repo.slots.value.isNotEmpty()) return@runBlocking
            Thread.sleep(50)
        }
    }

    protected fun shot(name: String, dark: Boolean = false, action: (() -> Unit)? = null, setup: (Navigator) -> Unit = {}) {
        val nav = Navigator()
        setup(nav)
        rule.mainClock.autoAdvance = false
        rule.setContent { MithrilTheme(dark = dark) { AppRoot(nav) } }
        repeat(40) { rule.mainClock.advanceTimeBy(100); shadowOf(Looper.getMainLooper()).idle() }
        if (action != null) {
            action()
            repeat(30) { rule.mainClock.advanceTimeBy(100); shadowOf(Looper.getMainLooper()).idle() }
        }
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

class TodayShot : ShotBase() {
    @Test fun today() { seed(); shot("01_oggi") }
}

class TodayDarkShot : ShotBase() {
    @Test fun todayDark() { seed(); shot("01b_oggi_scuro", dark = true) }
}

class PlannerShot : ShotBase() {
    @Test fun planner() { seed(); shot("02_planner") { it.select(Tab.PLANNER) } }
}

class LibraryShot : ShotBase() {
    @Test fun library() { seed(); shot("03_libreria") { it.select(Tab.LIBRARY) } }
}

class LibraryEmptyShot : ShotBase() {
    @Test fun libraryEmpty() { seed(empty = true); shot("03b_libreria_vuota") { it.select(Tab.LIBRARY) } }
}

class WeightShot : ShotBase() {
    @Test fun weight() { seed(); shot("04_peso") { it.select(Tab.WEIGHT) } }
}

class WeightDarkShot : ShotBase() {
    @Test fun weightDark() { seed(); shot("04b_peso_scuro", dark = true) { it.select(Tab.WEIGHT) } }
}

class WeighSheetShot : ShotBase() {
    @Test fun weigh() { seed(); shot("05_pesata") { it.select(Tab.WEIGHT); it.sheet = SheetKind.Weigh() } }
}

class SlotSheetShot : ShotBase() {
    @Test fun slot() { seed(); shot("06_nuovo_slot") { it.select(Tab.PLANNER); it.sheet = SheetKind.NewSlot(LocalDate.now().plusDays(1)) } }
}

class ReviewShot : ShotBase() {
    @Test fun review() { seed(); shot("07_revisione") { it.push(Dest.Review(Week.start(LocalDate.now()))) } }
}

class PlanWeekShot : ShotBase() {
    @Test fun plan() { seed(); shot("08_pianifica") { it.push(Dest.PlanWeek(Week.start(LocalDate.now()).plusWeeks(1))) } }
}

class ExternalShot : ShotBase() {
    @Test fun external() {
        seed()
        val repo = Repo.get(ApplicationProvider.getApplicationContext<Application>())
        val v = runBlocking { repo.dao.videoByYoutubeId("aaaaaaaaaa4")!! }
        shot("09_video_esterno") { it.push(Dest.External(SessionRequest(null, listOf(v.id), false), v.id)) }
    }
}

class CompletionShot : ShotBase() {
    @Test fun completion() {
        seed()
        val repo = Repo.get(ApplicationProvider.getApplicationContext<Application>())
        val ids = runBlocking { listOf(repo.dao.videoByYoutubeId("aaaaaaaaaa1")!!.id, repo.dao.videoByYoutubeId("aaaaaaaaaa2")!!.id) }
        shot("10_completamento") { it.push(Dest.Completion(-1, SessionRequest(null, ids, false), 2)) }
    }
}

class AddLinkShot : ShotBase() {
    @Test fun addLink() { seed(); shot("11_aggiungi_video") { it.select(Tab.LIBRARY); it.sheet = SheetKind.AddLink } }
}

class EditVideoShot : ShotBase() {
    @Test fun editVideo() {
        seed()
        val repo = Repo.get(ApplicationProvider.getApplicationContext<Application>())
        val v = runBlocking { repo.dao.videoByYoutubeId("aaaaaaaaaa2")!! }
        shot("12_modifica_video") { it.select(Tab.LIBRARY); it.sheet = SheetKind.AddVideo(edit = v) }
    }
}

class StatsShot : ShotBase() {
    @Test fun stats() { seed(); shot("13_statistiche") { it.select(Tab.STATS) } }
}

class StatsDarkShot : ShotBase() {
    @Test fun statsDark() { seed(); shot("13b_statistiche_scuro", dark = true) { it.select(Tab.STATS) } }
}

class StatsLowerShot : ShotBase() {
    @Test fun statsLower() {
        seed()
        shot("13c_statistiche_sotto", action = { rule.onNodeWithText("Settimana per settimana").performScrollTo() }) { it.select(Tab.STATS) }
    }
}

class StatsBottomShot : ShotBase() {
    @Test fun statsBottom() {
        seed()
        shot("13d_statistiche_fondo", action = { rule.onNodeWithText("Abitudini").performScrollTo() }) { it.select(Tab.STATS) }
    }
}

class PlanWeekPickShot : ShotBase() {
    @Test fun planPick() {
        seed()
        shot("08b_pianifica_piu_giorni", action = {
            repeat(2) {
                rule.onNodeWithText("Avanti").performClick()
                repeat(10) { rule.mainClock.advanceTimeBy(100); shadowOf(Looper.getMainLooper()).idle() }
            }
            rule.onNodeWithText("Core 10'").performClick()
            listOf("Lunedì", "Mercoledì", "Venerdì").forEach { d ->
                rule.mainClock.advanceTimeBy(300); shadowOf(Looper.getMainLooper()).idle()
                rule.onNodeWithText(d).performClick()
            }
        }) { it.push(Dest.PlanWeek(Week.start(LocalDate.now()).plusWeeks(1))) }
    }
}

