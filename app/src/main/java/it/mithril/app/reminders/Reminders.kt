package it.mithril.app.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import it.mithril.app.MainActivity
import it.mithril.app.R
import it.mithril.app.data.Repo
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Sessions
import it.mithril.app.widget.Widgets

/** Promemoria: un solo allarme esatto per la prossima sessione e uno per la revisione della domenica. */
object Reminders {
    const val CHANNEL_SESSIONS = "sessions"
    const val CHANNEL_REVIEW = "review"
    private const val ACTION_SESSION = "it.mithril.app.REMIND_SESSION"
    private const val ACTION_REVIEW = "it.mithril.app.REMIND_REVIEW"
    private const val ACTION_MIDNIGHT = "it.mithril.app.MIDNIGHT"
    private const val EXTRA_SLOT = "slot"
    private const val REQ_SESSION = 1
    private const val REQ_REVIEW = 2
    private const val REQ_MIDNIGHT = 3
    const val NOTIF_SESSION = 100
    const val NOTIF_REVIEW = 200

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_SESSIONS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REVIEW, context.getString(R.string.channel_review), NotificationManager.IMPORTANCE_LOW))
    }

    suspend fun reschedule(context: Context) {
        val repo = Repo.get(context)
        val settings = repo.settings.now()
        val am = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val logs = repo.dao.logsFrom(today.toEpochDay())
        val doneSlots = logs.mapNotNull { it.slotId }.toSet()
        val loggedDays = logs.map { it.date }.toSet()
        val next = repo.dao.slotsFrom(today.toEpochDay())
            .filter { it.id !in doneSlots && it.date !in loggedDays }
            .map { it to LocalDate.ofEpochDay(it.date).atStartOfDay().plusMinutes((it.timeMin - settings.reminderLeadMin).toLong()) }
            .filter { it.second.isAfter(now) }
            .minByOrNull { it.second }
        val sessionPi = pending(context, ACTION_SESSION, REQ_SESSION, next?.first?.id)
        if (next == null) am.cancel(sessionPi) else setExact(am, next.second, sessionPi)

        // A mezzanotte i widget passano al giorno nuovo (sessione di oggi, passi, pesata).
        setExact(am, today.plusDays(1).atStartOfDay().plusMinutes(1), pending(context, ACTION_MIDNIGHT, REQ_MIDNIGHT, null))

        val reviewPi = pending(context, ACTION_REVIEW, REQ_REVIEW, null)
        if (!settings.reviewReminder) am.cancel(reviewPi)
        else {
            var at = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atStartOfDay().plusMinutes(settings.reviewReminderMin.toLong())
            if (!at.isAfter(now)) at = at.plusWeeks(1)
            setExact(am, at, reviewPi)
        }
    }

    private fun pending(context: Context, action: String, req: Int, slotId: Long?): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action).apply { if (slotId != null) putExtra(EXTRA_SLOT, slotId) }
        return PendingIntent.getBroadcast(context, req, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("ScheduleExactAlarm", "MissingPermission")
    private fun setExact(am: AlarmManager, at: LocalDateTime, pi: PendingIntent) {
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    internal suspend fun fire(context: Context, intent: Intent) {
        val repo = Repo.get(context)
        when (intent.action) {
            ACTION_MIDNIGHT -> Widgets.updateAll(context)
            ACTION_SESSION -> {
                val slot = repo.dao.slot(intent.getLongExtra(EXTRA_SLOT, -1)) ?: return
                if (repo.dao.logsFrom(slot.date).any { it.slotId == slot.id || it.date == slot.date }) return
                val videos = Sessions.videosOf(slot.videoIdList, repo.dao.videosNow())
                if (videos.isEmpty()) return
                val mini = Sessions.miniFor(videos, repo.dao.videosNow())
                val lead = repo.settings.now().reminderLeadMin
                val b = NotificationCompat.Builder(context, CHANNEL_SESSIONS)
                    .setSmallIcon(R.drawable.ic_stat_notification)
                    .setColor(0xFFB0553A.toInt())
                    .setContentTitle("Tra $lead minuti: ${Sessions.title(videos, short = true)}")
                    .setContentText("${Fmt.time(slot.timeMin)} · ${slot.place} · ${Fmt.minutes(Sessions.totalSec(videos))}")
                    .setContentIntent(MainActivity.intent(context, MainActivity.OPEN_TODAY, null, 10))
                    .setAutoCancel(true)
                    .addAction(0, "Avvia", MainActivity.intent(context, MainActivity.START, slot.id, 11))
                if (mini != null) b.addAction(0, "Versione mini", MainActivity.intent(context, MainActivity.START_MINI, slot.id, 12))
                notify(context, NOTIF_SESSION, b)
            }
            ACTION_REVIEW -> {
                val b = NotificationCompat.Builder(context, CHANNEL_REVIEW)
                    .setSmallIcon(R.drawable.ic_stat_notification)
                    .setColor(0xFFB0553A.toInt())
                    .setContentTitle("Revisione della settimana")
                    .setContentText("5 minuti per chiudere la settimana, poi si pianifica.")
                    .setContentIntent(MainActivity.intent(context, MainActivity.OPEN_REVIEW, null, 20))
                    .setAutoCancel(true)
                notify(context, NOTIF_REVIEW, b)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, id: Int, b: NotificationCompat.Builder) {
        if (!canNotify(context)) return
        runCatching { NotificationManagerCompat.from(context).notify(id, b.build()) }
    }

    fun cancelSessionNotification(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIF_SESSION)
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        receiverScope.launch {
            try {
                Reminders.fire(context.applicationContext, intent)
                Reminders.reschedule(context.applicationContext)
            } finally { pending.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        receiverScope.launch {
            try { Reminders.reschedule(context.applicationContext) } finally { pending.finish() }
        }
    }
}
