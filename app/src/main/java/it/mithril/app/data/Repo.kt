package it.mithril.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import it.mithril.app.domain.Trend
import it.mithril.app.domain.Week

/** Accesso unico ai dati dell'app: flussi osservabili + azioni che aggiornano widget e promemoria. */
class Repo(private val context: Context) {
    val dao = MithrilDb.get(context).dao()
    val settings = SettingsRepo(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    val videos: StateFlow<List<VideoEntity>?> = dao.videos().stateIn(scope, SharingStarted.Eagerly, null)
    val slots: StateFlow<List<SlotEntity>> = dao.slots().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val logs: StateFlow<List<SessionLogEntity>> = dao.logs().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val weights: StateFlow<List<WeightEntity>?> = dao.weights().stateIn(scope, SharingStarted.Eagerly, null)
    val weekPlans: StateFlow<List<WeekPlanEntity>> = dao.weekPlans().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val reviews: StateFlow<List<ReviewAnswerEntity>> = dao.reviewAnswers().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val nutrition: StateFlow<List<NutritionDayEntity>> = dao.nutritionDays().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val appSettings: StateFlow<AppSettings> = settings.flow.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    /** Hook per widget e promemoria, collegati da [it.mithril.app.MithrilApp]. */
    var onDataChanged: (suspend () -> Unit)? = null
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        scope.launch {
            changes.collectLatest {
                delay(150)
                runCatching { onDataChanged?.invoke() }.onFailure { Log.w("MithrilRepo", "onDataChanged failed", it) }
            }
        }
    }

    fun refreshToday() { val now = LocalDate.now(); if (now != _today.value) _today.value = now }

    private fun changed() { changes.tryEmit(Unit) }

    fun goals(): Map<Long, Int> = weekPlans.value.associate { it.weekStart to it.goalSessions }

    // --- Video ---
    suspend fun addVideo(meta: VideoMeta, tags: Set<String>, miniId: Long?, startSec: Int = 0, endSec: Int? = null): Long {
        val id = dao.upsertVideoAtomic(meta, Tags.join(tags), miniId, startSec, endSec)
        changed()
        return id
    }
    suspend fun updateVideo(v: VideoEntity) { dao.updateVideo(v); changed() }
    suspend fun deleteVideo(id: Long) {
        val v = dao.video(id)
        dao.deleteVideoAtomic(id, today.value.toEpochDay())
        if (v != null) LocalVideos.forget(context, v)
        changed()
    }
    /** Intro e outro da saltare: [end] null = fino alla fine. */
    suspend fun setTrim(id: Long, start: Int, end: Int?) { dao.setTrim(id, start.coerceAtLeast(0), end); changed() }
    fun learnDuration(id: Long, sec: Int) { if (sec > 0) scope.launch { dao.fillDuration(id, sec) } }
    fun markEmbeddable(id: Long, ok: Boolean) { scope.launch { dao.setEmbeddable(id, ok) } }

    // --- Planner ---
    suspend fun saveSlot(s: SlotEntity): Long { val id = dao.upsertSlot(s); changed(); return if (s.id != 0L) s.id else id }
    suspend fun deleteSlot(id: Long) { dao.deleteSlot(id); changed() }
    suspend fun replaceWeekRange(weekStart: LocalDate, from: LocalDate, newSlots: List<SlotEntity>, goal: Int) {
        dao.replaceWeekWithGoal(from.toEpochDay(), weekStart.toEpochDay() + 6, newSlots, weekStart.toEpochDay(), goal.coerceIn(1, 7))
        changed()
    }
    suspend fun setWeekGoal(start: LocalDate, goal: Int) { dao.upsertWeekPlan(WeekPlanEntity(start.toEpochDay(), goal.coerceIn(1, 7))); changed() }
    suspend fun saveReview(start: LocalDate, obstacle: String?, day: LocalDate?) =
        dao.upsertReview(ReviewAnswerEntity(start.toEpochDay(), obstacle, day?.toEpochDay()))

    /** Ora e luogo ereditati dall'ultimo slot dello stesso giorno della settimana. */
    suspend fun lastSlotLike(date: LocalDate): SlotEntity? {
        val past = dao.slotsBefore(date.toEpochDay())
        return past.firstOrNull { LocalDate.ofEpochDay(it.date).dayOfWeek == date.dayOfWeek } ?: past.firstOrNull()
    }

    // --- Sessioni ---
    suspend fun logSession(slotId: Long?, mini: Boolean, ratio: Float, videoIds: List<Long>, title: String, durationSec: Int): Long {
        val log = SessionLogEntity(
            slotId = slotId, date = LocalDate.now().toEpochDay(),
            kind = if (mini) SessionLogEntity.KIND_MINI else SessionLogEntity.KIND_FULL,
            watchedRatio = ratio, fatigue = null, note = null, completedAt = System.currentTimeMillis(),
            title = title, videoIds = videoIds.joinToString(","), durationSec = durationSec,
        )
        val id = dao.logSessionAtomic(log, videoIds)
        changed()
        return id
    }
    suspend fun finishLog(id: Long, fatigue: Int, note: String?) {
        dao.log(id)?.let { dao.updateLog(it.copy(fatigue = fatigue, note = note?.trim()?.takeIf { n -> n.isNotEmpty() })) }
    }
    suspend fun undoLog(id: Long) { dao.undoLogAtomic(id); changed() }

    // --- Peso ---
    suspend fun saveWeight(date: LocalDate, kg: Double) {
        dao.upsertWeight(WeightEntity(date.toEpochDay(), (kg * 10).let { Math.round(it) / 10.0 }))
        val all = dao.weightsNow()
        if (all.size >= Trend.MIN_WEIGH_INS) Trend.of(all.map { it.date to it.kg }, LocalDate.now().toEpochDay()).at(LocalDate.now().toEpochDay())?.let { settings.fillGoalStart(it) }
        changed()
    }
    suspend fun deleteWeight(date: Long) { dao.deleteWeight(date); changed() }
    suspend fun restoreWeight(w: WeightEntity) { dao.upsertWeight(w); changed() }

    // --- Calorie / Nutrizione ---
    suspend fun syncNutritionRange(from: Long, to: Long, rows: List<NutritionDayEntity>) {
        dao.replaceNutritionRange(from, to, rows)
    }
    suspend fun clearNutrition() { dao.clearNutrition() }

    // --- Impostazioni (con notifica a widget e promemoria) ---
    suspend fun setGoal(targetKg: Double?, startKg: Double?, startDate: Long) { settings.setGoal(targetKg, startKg, startDate); changed() }
    suspend fun setRate(v: Double) { settings.setRate(v); changed() }
    suspend fun setStepsGoal(v: Int) { settings.setStepsGoal(v); changed() }
    suspend fun setCaloriesGoal(v: Int) { settings.setCaloriesGoal(v); changed() }
    suspend fun setProteinGoal(v: Int?) { settings.setProteinGoal(v); changed() }
    suspend fun setNutritionSource(pkg: String?) { settings.setNutritionSource(pkg); changed() }
    suspend fun setReminderLead(v: Int) { settings.setReminderLead(v); changed() }
    suspend fun setReviewReminder(on: Boolean) { settings.setReviewReminder(on); changed() }
    suspend fun setCastDevice(id: String?, name: String?) { settings.setCastDevice(id, name); changed() }

    fun weekGoal(start: LocalDate) = Week.goalFor(start, goals())

    companion object {
        @Volatile private var instance: Repo? = null
        fun get(context: Context): Repo = instance ?: synchronized(this) {
            instance ?: Repo(context.applicationContext).also { instance = it }
        }
    }
}
