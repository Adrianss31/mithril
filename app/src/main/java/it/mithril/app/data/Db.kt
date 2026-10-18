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
) {
    val tagList: List<String> get() = Tags.parse(tags)
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
    @Query("UPDATE video SET timesDone = timesDone + :delta WHERE id IN (:ids)") abstract suspend fun bumpTimesDone(ids: List<Long>, delta: Int)
    @Query("UPDATE video SET embeddable = :ok WHERE id = :id") abstract suspend fun setEmbeddable(id: Long, ok: Boolean)
    @Query("UPDATE video SET durationSec = :sec WHERE id = :id AND (durationSec IS NULL OR durationSec = 0)") abstract suspend fun fillDuration(id: Long, sec: Int)

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

    @Transaction
    open suspend fun replaceWeekSlots(from: Long, to: Long, slots: List<SlotEntity>) {
        deleteSlotsBetween(from, to)
        slots.forEach { upsertSlot(it) }
    }
}

@Database(
    entities = [VideoEntity::class, SlotEntity::class, SessionLogEntity::class, WeightEntity::class, WeekPlanEntity::class, ReviewAnswerEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MithrilDb : RoomDatabase() {
    abstract fun dao(): MithrilDao

    companion object {
        @Volatile private var instance: MithrilDb? = null
        fun get(context: Context): MithrilDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, MithrilDb::class.java, "mithril.db").build().also { instance = it }
        }
    }
}
