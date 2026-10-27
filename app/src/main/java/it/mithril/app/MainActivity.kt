package it.mithril.app

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import it.mithril.app.ui.startSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import it.mithril.app.data.Repo
import it.mithril.app.domain.Week
import it.mithril.app.health.Health
import it.mithril.app.reminders.Reminders
import it.mithril.app.ui.AppRoot
import it.mithril.app.ui.Dest
import it.mithril.app.ui.Navigator
import it.mithril.app.ui.SheetKind
import it.mithril.app.ui.Tab
import it.mithril.app.ui.theme.MithrilTheme
import it.mithril.app.update.Updates
import it.mithril.app.widget.WidgetRefresh
import it.mithril.app.widget.Widgets

class MithrilApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Reminders.createChannels(this)
        val repo = Repo.get(this)
        repo.onDataChanged = {
            Widgets.updateAll(this)
            Reminders.reschedule(this)
        }
        repo.scope.launch { Reminders.reschedule(this@MithrilApp) }
        WidgetRefresh.schedule(this)
    }
}

class MainActivity : ComponentActivity() {
    private val nav = Navigator()
    private val pending = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pending.value = intent
        val repo = Repo.get(this)
        setContent {
            MithrilTheme {
                val p by pending.collectAsStateWithLifecycle()
                val videos by repo.videos.collectAsStateWithLifecycle()
                LaunchedEffect(p, videos) {
                    val i = p ?: return@LaunchedEffect
                    if (videos == null) return@LaunchedEffect
                    handle(i, repo)
                    pending.value = null
                }
                AppRoot(nav)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pending.value = intent
    }

    override fun onResume() {
        super.onResume()
        val repo = Repo.get(this)
        repo.refreshToday()
        lifecycleScope.launch { Health.refresh(applicationContext); Widgets.updateAll(applicationContext) }
        lifecycleScope.launch { Updates.autoCheck(applicationContext) }
    }

    private suspend fun handle(i: Intent, repo: Repo) {
        when (i.action) {
            START, START_MINI -> {
                Reminders.cancelSessionNotification(this)
                val slot = repo.dao.slot(i.getLongExtra(EXTRA_SLOT, -1)) ?: return
                nav.popToRoot(); nav.sheet = null; nav.select(Tab.TODAY)
                nav.startSlot(slot, i.action == START_MINI, repo.videos.value ?: emptyList(), i.getBooleanExtra(EXTRA_CAST, false))
            }
            OPEN_TODAY -> { nav.popToRoot(); nav.select(Tab.TODAY) }
            OPEN_PLANNER -> { nav.popToRoot(); nav.select(Tab.PLANNER) }
            OPEN_WEIGHT -> { nav.popToRoot(); nav.select(Tab.WEIGHT) }
            OPEN_REVIEW -> { nav.popToRoot(); nav.push(Dest.Review(Week.reviewWeek(LocalDate.now()))) }
            WEIGH -> { nav.select(Tab.WEIGHT); nav.sheet = SheetKind.Weigh() }
            OPEN_GOAL -> { nav.popToRoot(); nav.select(Tab.WEIGHT); nav.push(Dest.Settings) }
        }
    }

    companion object {
        const val START = "it.mithril.app.START"
        const val START_MINI = "it.mithril.app.START_MINI"
        const val OPEN_TODAY = "it.mithril.app.OPEN_TODAY"
        const val OPEN_PLANNER = "it.mithril.app.OPEN_PLANNER"
        const val OPEN_WEIGHT = "it.mithril.app.OPEN_WEIGHT"
        const val OPEN_REVIEW = "it.mithril.app.OPEN_REVIEW"
        const val WEIGH = "it.mithril.app.WEIGH"
        const val OPEN_GOAL = "it.mithril.app.OPEN_GOAL"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_CAST = "cast"

        fun intent(context: Context, action: String, slotId: Long?, req: Int): PendingIntent {
            val i = Intent(context, MainActivity::class.java).setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (slotId != null) i.putExtra(EXTRA_SLOT, slotId)
            return PendingIntent.getActivity(context, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
