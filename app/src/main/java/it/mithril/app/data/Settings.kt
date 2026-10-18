package it.mithril.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val goalKg: Double? = null,
    val weeklyRateKg: Double = 0.25,
    val goalStartKg: Double? = null,
    val goalStartDate: Long? = null,
    val stepsGoal: Int = 7000,
    val reminderLeadMin: Int = 30,
    val reviewReminder: Boolean = true,
    val reviewReminderMin: Int = 20 * 60,
    val healthAsked: Boolean = false,
    val lastUpdateCheck: Long = 0,
)

private val Context.store: DataStore<Preferences> by preferencesDataStore("mithril_settings")

class SettingsRepo(private val context: Context) {
    private object K {
        val goalKg = doublePreferencesKey("goal_kg")
        val rate = doublePreferencesKey("weekly_rate_kg")
        val startKg = doublePreferencesKey("goal_start_kg")
        val startDate = longPreferencesKey("goal_start_date")
        val steps = intPreferencesKey("steps_goal")
        val lead = intPreferencesKey("reminder_lead_min")
        val review = booleanPreferencesKey("review_reminder")
        val reviewMin = intPreferencesKey("review_reminder_min")
        val healthAsked = booleanPreferencesKey("health_asked")
        val lastUpdate = longPreferencesKey("last_update_check")
    }

    val flow: Flow<AppSettings> = context.store.data.map { p ->
        AppSettings(
            goalKg = p[K.goalKg],
            weeklyRateKg = p[K.rate] ?: 0.25,
            goalStartKg = p[K.startKg],
            goalStartDate = p[K.startDate],
            stepsGoal = p[K.steps] ?: 7000,
            reminderLeadMin = p[K.lead] ?: 30,
            reviewReminder = p[K.review] ?: true,
            reviewReminderMin = p[K.reviewMin] ?: (20 * 60),
            healthAsked = p[K.healthAsked] ?: false,
            lastUpdateCheck = p[K.lastUpdate] ?: 0,
        )
    }

    suspend fun now(): AppSettings = flow.first()

    /** Nuovo obiettivo: il peso iniziale della barra è il trend di oggi. */
    suspend fun setGoal(targetKg: Double?, startKg: Double?, startDate: Long) = context.store.edit { p ->
        if (targetKg == null) { p.remove(K.goalKg); p.remove(K.startKg); p.remove(K.startDate) }
        else {
            p[K.goalKg] = targetKg
            if (startKg != null) p[K.startKg] = startKg else p.remove(K.startKg)
            p[K.startDate] = startDate
        }
    }
    suspend fun setRate(v: Double) = context.store.edit { it[K.rate] = v }
    suspend fun setStepsGoal(v: Int) = context.store.edit { it[K.steps] = v }
    suspend fun setReminderLead(v: Int) = context.store.edit { it[K.lead] = v }
    suspend fun setReviewReminder(on: Boolean) = context.store.edit { it[K.review] = on }
    suspend fun setHealthAsked() = context.store.edit { it[K.healthAsked] = true }
    suspend fun setLastUpdateCheck(t: Long) = context.store.edit { it[K.lastUpdate] = t }
    /** Se l'obiettivo non ha ancora un peso di partenza, lo fissa alla prima occasione utile. */
    suspend fun fillGoalStart(kg: Double) = context.store.edit { if (it[K.goalKg] != null && it[K.startKg] == null) it[K.startKg] = kg }
}
