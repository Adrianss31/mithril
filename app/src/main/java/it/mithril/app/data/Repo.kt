package it.mithril.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val appSettings: StateFlow<AppSettings> = settings.flow.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    /** Hook per widget e promemoria, collegati da [it.mithril.app.MithrilApp]. */
    var onDataChanged: (suspend () -> Unit)? = null

    init {
        scope.launch { while (true) { delay(60_000); refreshToday() } }
    }

    fun refreshToday() { val now = LocalDate.now(); if (now != _today.value) _today.value = now }

    private fun changed() { scope.launch { runCatching { onDataChanged?.invoke() } } }

    fun goals(): Map<Long, Int> = weekPlans.value.associate { it.weekStart to it.goalSessions }

    // --- Video ---
    suspend fun addVideo(meta: VideoMeta, tags: Set<String>, miniId: Long?): Long {
        val existing = dao.videoByYoutubeId(meta.youtubeId)
        val id = if (existing != null) {
            dao.updateVideo(existing.copy(title = meta.title, localUri = meta.localUri ?: existing.localUri, channel = meta.channel, durationSec = meta.durationSec ?: existing.durationSec,
                tags = Tags.join(tags), miniVideoId = miniId, embeddable = meta.embeddable ?: existing.embeddable))
            existing.id
        } else dao.insertVideo(VideoEntity(youtubeId = meta.youtubeId, title = meta.title, channel = meta.channel, durationSec = meta.durationSec,
            thumbnailUrl = meta.thumbnailUrl, tags = Tags.join(tags), miniVideoId = miniId, embeddable = meta.embeddable, localUri = meta.localUri))
        changed(); return id
    }
    suspend fun updateVideo(v: VideoEntity) { dao.updateVideo(v); changed() }
    suspend fun deleteVideo(id: Long) {
        dao.clearMiniRefs(id)
        // Toglie il video dagli slot futuri; gli slot rimasti vuoti spariscono.
        dao.slotsFrom(today.value.toEpochDay()).forEach { s ->
            if (id in s.videoIdList) {
                val rest = s.videoIdList - id
                if (rest.isEmpty()) dao.deleteSlot(s.id) else dao.upsertSlot(s.copy(videoIds = rest.joinToString(",")))
            }
        }
        dao.video(id)?.let { LocalVideos.forget(context, it) }
        dao.deleteVideo(id); changed()
    }
    fun learnDuration(id: Long, sec: Int) { if (sec > 0) scope.launch { dao.fillDuration(id, sec) } }
    fun markEmbeddable(id: Long, ok: Boolean) { scope.launch { dao.setEmbeddable(id, ok) } }

    // --- Planner ---
    suspend fun saveSlot(s: SlotEntity): Long { val id = dao.upsertSlot(s); changed(); return if (s.id != 0L) s.id else id }
    suspend fun deleteSlot(id: Long) { dao.deleteSlot(id); changed() }
    suspend fun replaceWeek(start: LocalDate, newSlots: List<SlotEntity>, goal: Int) {
        val from = start.toEpochDay()
        dao.replaceWeekSlots(from, from + 6, newSlots)
        dao.upsertWeekPlan(WeekPlanEntity(from, goal))
        changed()
    }
    suspend fun setWeekGoal(start: LocalDate, goal: Int) { dao.upsertWeekPlan(WeekPlanEntity(start.toEpochDay(), goal.coerceIn(1, 7))); changed() }
    suspend fun saveReview(start: LocalDate, obstacle: String?, day: LocalDate?) =
        dao.upsertReview(ReviewAnswerEntity(start.toEpochDay(), obstacle, day?.toEpochDay()))

    /** Ora e luogo ereditati dall'ultimo slot dello stesso giorno della settimana. */
    suspend fun lastSlotLike(date: LocalDate): SlotEntity? =
        dao.slotsBefore(date.toEpochDay()).firstOrNull { LocalDate.ofEpochDay(it.date).dayOfWeek == date.dayOfWeek }
            ?: dao.slotsBefore(date.toEpochDay()).firstOrNull()

    // --- Sessioni ---
    suspend fun logSession(slotId: Long?, mini: Boolean, ratio: Float, videoIds: List<Long>, title: String, durationSec: Int): Long {
        val id = dao.insertLog(SessionLogEntity(
            slotId = slotId, date = LocalDate.now().toEpochDay(),
            kind = if (mini) SessionLogEntity.KIND_MINI else SessionLogEntity.KIND_FULL,
            watchedRatio = ratio, fatigue = 6, note = null, completedAt = System.currentTimeMillis(),
            title = title, videoIds = videoIds.joinToString(","), durationSec = durationSec,
        ))
        if (videoIds.isNotEmpty()) dao.bumpTimesDone(videoIds, 1)
        changed(); return id
    }
    suspend fun finishLog(id: Long, fatigue: Int, note: String?) {
        dao.log(id)?.let { dao.updateLog(it.copy(fatigue = fatigue, note = note?.trim()?.takeIf { n -> n.isNotEmpty() })) }
    }
    suspend fun undoLog(id: Long) {
        val log = dao.log(id) ?: return
        val ids = log.videoIds.split(',').mapNotNull { it.toLongOrNull() }
        if (ids.isNotEmpty()) dao.bumpTimesDone(ids, -1)
        dao.deleteLog(id); changed()
    }

    // --- Peso ---
    suspend fun saveWeight(date: LocalDate, kg: Double) {
        dao.upsertWeight(WeightEntity(date.toEpochDay(), (kg * 10).let { Math.round(it) / 10.0 }))
        val all = dao.weightsNow()
        if (all.size >= Trend.MIN_WEIGH_INS) Trend.of(all.map { it.date to it.kg }, LocalDate.now().toEpochDay()).at(LocalDate.now().toEpochDay())?.let { settings.fillGoalStart(it) }
        changed()
    }
    suspend fun deleteWeight(date: Long) { dao.deleteWeight(date); changed() }
    suspend fun restoreWeight(w: WeightEntity) { dao.upsertWeight(w); changed() }

    fun weekGoal(start: LocalDate) = Week.goalFor(start, goals())

    companion object {
        @Volatile private var instance: Repo? = null
        fun get(context: Context): Repo = instance ?: synchronized(this) {
            instance ?: Repo(context.applicationContext).also { instance = it }
        }
    }
}
