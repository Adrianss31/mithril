package it.mithril.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Video salvato in Libreria. [tags] è un elenco separato da virgole (vedi [Tags]). */
@Entity(tableName = "video", indices = [Index(value = ["youtubeId"], unique = true)])
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val youtubeId: String,
    val title: String,
    val channel: String,
    val durationSec: Int?,
    val thumbnailUrl: String,
    val tags: String,
    val miniVideoId: Long? = null,
    val embeddable: Boolean? = null,
    val timesDone: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Video salvato sul telefono (content:// con permesso persistente); null per i video YouTube. */
    val localUri: String? = null,
    /** Secondi da saltare all'inizio (intro). */
    val startSec: Int = 0,
    /** Dove fermarsi (prima dell'outro), in secondi dall'inizio del video; null fino alla fine. */
    val endSec: Int? = null,
) {
    val tagList: List<String> get() = Tags.parse(tags)
    val isLocal: Boolean get() = localUri != null
    val trimmed: Boolean get() = startSec > 0 || endSec != null
    /** Durata effettiva, senza intro e outro; null se la durata non è ancora nota. */
    val playSec: Int? get() = playSec(durationSec ?: 0).takeIf { it > 0 }
    /** Durata effettiva per un video lungo [full] secondi. */
    fun playSec(full: Int): Int = ((endSec ?: full) - startSec).coerceAtLeast(0)
}

/** Slot del planner: giorno (epochDay), ora (minuti dalla mezzanotte), luogo e sequenza ordinata di video. */
@Entity(tableName = "slot", indices = [Index("date")])
data class SlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val timeMin: Int,
    val place: String,
    val videoIds: String,
) {
    val videoIdList: List<Long> get() = videoIds.split(',').mapNotNull { it.trim().toLongOrNull() }
}

@Entity(tableName = "session_log", indices = [Index("date")])
data class SessionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slotId: Long?,
    val date: Long,
    val kind: String,
    val watchedRatio: Float,
    val fatigue: Int?,
    val note: String?,
    val completedAt: Long,
    val title: String,
    val videoIds: String,
    val durationSec: Int,
) {
    val isMini: Boolean get() = kind == KIND_MINI
    companion object { const val KIND_FULL = "FULL"; const val KIND_MINI = "MINI" }
}

@Entity(tableName = "weight")
data class WeightEntity(@PrimaryKey val date: Long, val kg: Double, val updatedAt: Long = System.currentTimeMillis())

@Entity(tableName = "week_plan")
data class WeekPlanEntity(@PrimaryKey val weekStart: Long, val goalSessions: Int)

@Entity(tableName = "review_answer")
data class ReviewAnswerEntity(@PrimaryKey val weekStart: Long, val obstacle: String?, val skippedDay: Long?, val answeredAt: Long = System.currentTimeMillis())

@Entity(tableName = "nutrition_day")
data class NutritionDayEntity(
    @PrimaryKey val date: Long,
    val kcal: Int,
    val proteinG: Float? = null,
    val carbsG: Float? = null,
    val fatG: Float? = null,
    val fiberG: Float? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
abstract class MithrilDao {
    @Query("SELECT * FROM video ORDER BY createdAt DESC") abstract fun videos(): Flow<List<VideoEntity>>
    @Query("SELECT * FROM video") abstract suspend fun videosNow(): List<VideoEntity>
    @Query("SELECT * FROM video WHERE id = :id") abstract suspend fun video(id: Long): VideoEntity?
    @Query("SELECT * FROM video WHERE youtubeId = :yt") abstract suspend fun videoByYoutubeId(yt: String): VideoEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertVideo(v: VideoEntity): Long
    @Update abstract suspend fun updateVideo(v: VideoEntity)
    @Query("DELETE FROM video WHERE id = :id") abstract suspend fun deleteVideo(id: Long)
    @Query("UPDATE video SET miniVideoId = NULL WHERE miniVideoId = :id") abstract suspend fun clearMiniRefs(id: Long)
    @Query("UPDATE video SET timesDone = MAX(0, timesDone + :delta) WHERE id IN (:ids)") abstract suspend fun bumpTimesDone(ids: List<Long>, delta: Int)
    @Query("UPDATE video SET embeddable = :ok WHERE id = :id") abstract suspend fun setEmbeddable(id: Long, ok: Boolean)
    @Query("UPDATE video SET durationSec = :sec WHERE id = :id AND (durationSec IS NULL OR durationSec = 0)") abstract suspend fun fillDuration(id: Long, sec: Int)
    @Query("UPDATE video SET startSec = :start, endSec = :end WHERE id = :id") abstract suspend fun setTrim(id: Long, start: Int, end: Int?)

    @Query("SELECT * FROM slot ORDER BY date, timeMin") abstract fun slots(): Flow<List<SlotEntity>>
    @Query("SELECT * FROM slot WHERE date >= :from AND date <= :to ORDER BY date, timeMin") abstract suspend fun slotsBetween(from: Long, to: Long): List<SlotEntity>
    @Query("SELECT * FROM slot WHERE id = :id") abstract suspend fun slot(id: Long): SlotEntity?
    @Query("SELECT * FROM slot WHERE date >= :from ORDER BY date, timeMin") abstract suspend fun slotsFrom(from: Long): List<SlotEntity>
    @Upsert abstract suspend fun upsertSlot(s: SlotEntity): Long
    @Query("DELETE FROM slot WHERE id = :id") abstract suspend fun deleteSlot(id: Long)
    @Query("DELETE FROM slot WHERE date >= :from AND date <= :to") abstract suspend fun deleteSlotsBetween(from: Long, to: Long)
    @Query("SELECT * FROM slot WHERE date < :before ORDER BY date DESC, timeMin DESC") abstract suspend fun slotsBefore(before: Long): List<SlotEntity>

    @Query("SELECT * FROM session_log ORDER BY completedAt") abstract fun logs(): Flow<List<SessionLogEntity>>
    @Query("SELECT * FROM session_log WHERE id = :id") abstract suspend fun log(id: Long): SessionLogEntity?
    @Query("SELECT * FROM session_log WHERE date >= :from") abstract suspend fun logsFrom(from: Long): List<SessionLogEntity>
    @Insert abstract suspend fun insertLog(l: SessionLogEntity): Long
    @Update abstract suspend fun updateLog(l: SessionLogEntity)
    @Query("DELETE FROM session_log WHERE id = :id") abstract suspend fun deleteLog(id: Long)

    @Query("SELECT * FROM weight ORDER BY date") abstract fun weights(): Flow<List<WeightEntity>>
    @Query("SELECT * FROM weight ORDER BY date") abstract suspend fun weightsNow(): List<WeightEntity>
    @Upsert abstract suspend fun upsertWeight(w: WeightEntity)
    @Query("DELETE FROM weight WHERE date = :date") abstract suspend fun deleteWeight(date: Long)

    @Query("SELECT * FROM week_plan") abstract fun weekPlans(): Flow<List<WeekPlanEntity>>
    @Upsert abstract suspend fun upsertWeekPlan(p: WeekPlanEntity)

    @Query("SELECT * FROM review_answer") abstract fun reviewAnswers(): Flow<List<ReviewAnswerEntity>>
    @Upsert abstract suspend fun upsertReview(r: ReviewAnswerEntity)

    @Query("SELECT * FROM nutrition_day ORDER BY date") abstract fun nutritionDays(): Flow<List<NutritionDayEntity>>
    @Query("SELECT * FROM nutrition_day ORDER BY date") abstract suspend fun nutritionDaysNow(): List<NutritionDayEntity>
    @Upsert abstract suspend fun upsertNutritionDays(days: List<NutritionDayEntity>)
    @Query("DELETE FROM nutrition_day WHERE date >= :from AND date <= :to") abstract suspend fun deleteNutritionBetween(from: Long, to: Long)
    @Query("DELETE FROM nutrition_day") abstract suspend fun clearNutrition()

    @Transaction
    open suspend fun replaceNutritionRange(from: Long, to: Long, rows: List<NutritionDayEntity>) {
        deleteNutritionBetween(from, to)
        if (rows.isNotEmpty()) upsertNutritionDays(rows)
    }

    @Transaction
    open suspend fun upsertVideoAtomic(meta: VideoMeta, tags: String, miniId: Long?, startSec: Int, endSec: Int?): Long {
        val existing = videoByYoutubeId(meta.youtubeId)
        return if (existing != null) {
            val keepTrim = startSec == 0 && endSec == null
            updateVideo(
                existing.copy(
                    title = meta.title,
                    localUri = meta.localUri ?: existing.localUri,
                    channel = meta.channel,
                    durationSec = meta.durationSec ?: existing.durationSec,
                    tags = tags,
                    miniVideoId = miniId,
                    embeddable = meta.embeddable ?: existing.embeddable,
                    startSec = if (keepTrim) existing.startSec else startSec,
                    endSec = if (keepTrim) existing.endSec else endSec,
                )
            )
            existing.id
        } else {
            insertVideo(
                VideoEntity(
                    youtubeId = meta.youtubeId,
                    title = meta.title,
                    channel = meta.channel,
                    durationSec = meta.durationSec,
                    thumbnailUrl = meta.thumbnailUrl,
                    tags = tags,
                    miniVideoId = miniId,
                    embeddable = meta.embeddable,
                    localUri = meta.localUri,
                    startSec = startSec,
                    endSec = endSec,
                )
            )
        }
    }

    @Transaction
    open suspend fun deleteVideoAtomic(id: Long, fromEpochDay: Long) {
        clearMiniRefs(id)
        // Toglie il video dagli slot futuri; gli slot rimasti vuoti spariscono.
        slotsFrom(fromEpochDay).forEach { s ->
            if (id in s.videoIdList) {
                val rest = s.videoIdList - id
                if (rest.isEmpty()) deleteSlot(s.id) else upsertSlot(s.copy(videoIds = rest.joinToString(",")))
            }
        }
        deleteVideo(id)
    }

    @Transaction
    open suspend fun logSessionAtomic(log: SessionLogEntity, videoIds: List<Long>): Long {
        val id = insertLog(log)
        if (videoIds.isNotEmpty()) bumpTimesDone(videoIds, 1)
        return id
    }

    @Transaction
    open suspend fun undoLogAtomic(id: Long) {
        val l = log(id) ?: return
        val ids = l.videoIds.split(',').mapNotNull { it.trim().toLongOrNull() }
        if (ids.isNotEmpty()) bumpTimesDone(ids, -1)
        deleteLog(id)
    }

    @Transaction
    open suspend fun replaceWeekSlots(from: Long, to: Long, slots: List<SlotEntity>) {
        val existingByDate = slotsBetween(from, to).groupBy { it.date }
        val newDates = slots.map { it.date }.toSet()
        existingByDate.forEach { (d, old) ->
            if (d !in newDates) old.forEach { deleteSlot(it.id) }
            else if (old.size > 1) old.drop(1).forEach { deleteSlot(it.id) }
        }
        slots.forEach { s ->
            val keepId = if (s.id != 0L) s.id else existingByDate[s.date]?.firstOrNull()?.id ?: 0L
            upsertSlot(if (keepId != s.id) s.copy(id = keepId) else s)
        }
    }

    @Transaction
    open suspend fun replaceWeekWithGoal(from: Long, to: Long, slots: List<SlotEntity>, weekStart: Long, goal: Int) {
        replaceWeekSlots(from, to, slots)
        upsertWeekPlan(WeekPlanEntity(weekStart, goal))
    }
}

@Database(
    entities = [VideoEntity::class, SlotEntity::class, SessionLogEntity::class, WeightEntity::class, WeekPlanEntity::class, ReviewAnswerEntity::class, NutritionDayEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class MithrilDb : RoomDatabase() {
    abstract fun dao(): MithrilDao

    companion object {
        @Volatile private var instance: MithrilDb? = null
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = db.execSQL("ALTER TABLE video ADD COLUMN localUri TEXT")
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE video ADD COLUMN startSec INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE video ADD COLUMN endSec INTEGER")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `nutrition_day` (`date` INTEGER NOT NULL, `kcal` INTEGER NOT NULL, `proteinG` REAL, `carbsG` REAL, `fatG` REAL, `fiberG` REAL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`))"
                )
            }
        }
        fun get(context: Context): MithrilDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, MithrilDb::class.java, "mithril.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { instance = it }
        }
    }
}
