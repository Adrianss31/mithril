package it.mithril.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import coil.ImageLoader
import coil.request.ImageRequest
import it.mithril.app.MainActivity
import it.mithril.app.data.Repo
import it.mithril.app.data.SessionProgress
import it.mithril.app.data.SlotEntity
import it.mithril.app.data.StepsCache
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Goal
import it.mithril.app.domain.Sessions
import it.mithril.app.domain.Trend
import it.mithril.app.health.Health
import it.mithril.app.health.HealthPermissionActivity
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

private val SMALL = 78.dp

private fun open(context: Context, action: String, slot: Long? = null): Action =
    actionStartActivity(Intent(context, MainActivity::class.java).setAction(action).apply { if (slot != null) putExtra(MainActivity.EXTRA_SLOT, slot) }
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))

private fun quickWeigh(context: Context): Action =
    actionStartActivity(Intent(context, QuickWeightActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))

/** Maschera bianca colorata dal tema (chiaro/scuro) del sistema. */
@Composable
private fun Tinted(bitmap: Bitmap, size: Dp, color: ColorProvider, modifier: GlanceModifier = GlanceModifier) =
    Image(ImageProvider(bitmap), null, modifier.size(size), colorFilter = ColorFilter.tint(color))

@Composable
private fun Icon(context: Context, name: String, size: Dp, color: ColorProvider, filled: Boolean = true) =
    Tinted(WidgetArt.glyph(context, name, filled), size, color)

@Composable
private fun Centered(content: @Composable () -> Unit) =
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }

// ---------- Passi · 1×1 ----------

private data class StepsData(val granted: Boolean, val steps: Int?, val goal: Int)

class StepsRingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        runCatching { Health.refresh(context) }
        val st = Health.state.value
        val d = StepsData(st.granted, st.today ?: StepsCache.today(context), Repo.get(context).settings.now().stepsGoal)
        val ring = WidgetArt.ring(0f, full = true)
        val fraction = ((d.steps ?: 0).toFloat() / d.goal).coerceIn(0f, 1f)
        val arc = WidgetArt.ring(fraction)
        val disc = WidgetArt.disc()
        provideContent {
            Centered {
                if (!d.granted) {
                    Box(
                        GlanceModifier.size(SMALL).clickable(actionStartActivity(Intent(context, HealthPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Tinted(disc, SMALL, WC.surface)
                        Tinted(WidgetArt.dashedCircle(), SMALL, WC.line2)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(context, "footprint", 22.dp, WC.ink2, filled = false)
                            Text("Collega", style = TextStyle(color = WC.ink2, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                } else {
                    val reached = (d.steps ?: 0) >= d.goal
                    Box(GlanceModifier.size(SMALL).clickable(open(context, MainActivity.OPEN_TODAY)), contentAlignment = Alignment.Center) {
                        Tinted(disc, SMALL, WC.surface)
                        Tinted(ring, SMALL, if (reached) WC.accent else WC.surface2)
                        if (!reached && fraction > 0f) Tinted(arc, SMALL, WC.accent)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (reached) Icon(context, "check_circle", 16.dp, WC.accent)
                            Text(
                                formatSteps(d.steps),
                                style = TextStyle(color = WC.ink, fontSize = if (reached) 15.sp else 16.sp, fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "7,2k"; sotto 1.000 il numero intero. */
internal fun formatSteps(steps: Int?): String = when {
    steps == null -> "—"
    steps < 1000 -> steps.toString()
    else -> String.format(Locale.ITALY, "%.1fk", steps / 1000.0)
}

class StepsRingWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StepsRingWidget() }

// ---------- Primo video · 1×1 ----------

private sealed interface FirstVideo {
    data class Next(val slot: SlotEntity, val number: Int, val thumb: Bitmap?, val progress: Float?) : FirstVideo
    data object Done : FirstVideo
    data object Rest : FirstVideo
}

private suspend fun loadFirstVideo(context: Context): FirstVideo {
    val repo = Repo.get(context)
    val today = LocalDate.now().toEpochDay()
    val slots = repo.dao.slotsBetween(today, today).sortedBy { it.timeMin }
    val logs = repo.dao.logsFrom(today).filter { it.date == today }
    val logged = logs.mapNotNull { it.slotId }.toSet()
    val pending = slots.filter { it.id !in logged }
    if (logs.isNotEmpty() && (pending.isEmpty() || logs.any { it.slotId == null })) return FirstVideo.Done
    val slot = pending.firstOrNull() ?: return FirstVideo.Rest
    val videos = Sessions.videosOf(slot.videoIdList, repo.dao.videosNow())
    if (videos.isEmpty()) return FirstVideo.Rest
    val p = SessionProgress.get(context, slot.id)?.takeIf { it.index in videos.indices }
    val index = p?.index ?: 0
    val thumb = videos[index].thumbnailUrl.takeIf { it.isNotBlank() }?.let { url ->
        runCatching {
            val req = ImageRequest.Builder(context).data(url).size(234, 234).allowHardware(false).build()
            (ImageLoader(context).execute(req).drawable as? BitmapDrawable)?.bitmap
        }.getOrNull()
    }
    val progress = p?.let { (it.watched / it.total.coerceAtLeast(1)).coerceIn(0f, 1f) }
    return FirstVideo.Next(slot, index + 1, thumb, progress)
}

class FirstVideoWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = loadFirstVideo(context)
        val disc = WidgetArt.disc()
        provideContent {
            Centered {
                when (d) {
                    is FirstVideo.Next -> Box(
                        GlanceModifier.size(SMALL).cornerRadius(24.dp).background(WC.surface2).clickable(open(context, MainActivity.START, d.slot.id)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (d.thumb != null) Image(ImageProvider(d.thumb), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(GlanceModifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Tinted(disc, 36.dp, WC.accent)
                            Icon(context, "play_arrow", 24.dp, WC.onAccent)
                        }
                        Box(GlanceModifier.fillMaxSize().padding(5.dp), contentAlignment = Alignment.TopStart) {
                            Box(GlanceModifier.size(20.dp), contentAlignment = Alignment.Center) {
                                Tinted(disc, 20.dp, WC.surface)
                                Text("${d.number}", style = TextStyle(color = WC.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                            }
                        }
                        if (d.progress != null) Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                            Row(GlanceModifier.fillMaxWidth().height(4.dp).background(WC.surface2)) {
                                Box(GlanceModifier.width(SMALL * d.progress).height(4.dp).background(WC.accent)) {}
                            }
                        }
                    }
                    FirstVideo.Done -> Box(
                        GlanceModifier.size(SMALL).cornerRadius(24.dp).background(WC.accent).clickable(open(context, MainActivity.OPEN_TODAY)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(context, "check_circle", 30.dp, WC.onAccent)
                            Text("fatta", style = TextStyle(color = WC.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                    FirstVideo.Rest -> Box(
                        GlanceModifier.size(SMALL).cornerRadius(24.dp).background(WC.accentSoft).clickable(open(context, MainActivity.OPEN_PLANNER)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(context, "bedtime", 26.dp, WC.accentInk)
                            Text("riposo", style = TextStyle(color = WC.accentInk, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

class FirstVideoWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = FirstVideoWidget() }

// ---------- Peso · 1×1 ----------

private data class SmallWeight(val count: Int, val value: Double?, val change: Double?, val weighedToday: Boolean)

class WeightSmallWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val w = Repo.get(context).dao.weightsNow()
        val t = LocalDate.now().toEpochDay()
        val enough = w.size >= Trend.MIN_WEIGH_INS
        val trend = if (enough) Trend.of(w.map { it.date to it.kg }, t) else null
        val d = SmallWeight(w.size, trend?.at(t) ?: w.lastOrNull()?.kg, trend?.recentChange(t)?.first, w.any { it.date == t })
        val disc = WidgetArt.disc()
        provideContent {
            Centered {
                if (d.value == null) Box(GlanceModifier.size(SMALL).clickable(quickWeigh(context)), contentAlignment = Alignment.Center) {
                    Tinted(disc, SMALL, WC.accent)
                    Icon(context, "monitor_weight", 34.dp, WC.onAccent, filled = false)
                } else Box(GlanceModifier.size(SMALL).cornerRadius(24.dp).background(WC.surface).clickable(quickWeigh(context)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Fmt.kg(d.value), style = TextStyle(color = WC.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                        if (d.change != null) Row(verticalAlignment = Alignment.CenterVertically) {
                            val arrow = when { d.change <= -0.05 -> "south_east"; d.change >= 0.05 -> "north_east"; else -> "east" }
                            Icon(context, arrow, 14.dp, WC.accentInk, filled = false)
                            Text(Fmt.kg(abs(d.change)), style = TextStyle(color = WC.accentInk, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        } else if (d.count < Trend.MIN_WEIGH_INS) Text("trend tra ${Trend.MIN_WEIGH_INS - d.count}", style = TextStyle(color = WC.ink3, fontSize = 11.sp))
                    }
                    if (d.weighedToday) Box(GlanceModifier.fillMaxSize().padding(5.dp), contentAlignment = Alignment.TopEnd) {
                        Box(GlanceModifier.size(18.dp), contentAlignment = Alignment.Center) {
                            Tinted(disc, 18.dp, WC.accent)
                            Icon(context, "check", 13.dp, WC.onAccent, filled = false)
                        }
                    }
                }
            }
        }
    }
}

class WeightSmallWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = WeightSmallWidget() }

// ---------- Traguardo · 4×1 ----------

private sealed interface GoalData {
    data class NoGoal(val trend: Double?) : GoalData
    data class Track(val start: Double, val goal: Double, val progress: Float, val done: Double, val left: Double, val eta: LocalDate?, val late: Boolean, val reachedOn: LocalDate?) : GoalData
}

private suspend fun loadGoal(context: Context): GoalData {
    val repo = Repo.get(context)
    val s = repo.settings.now()
    val w = repo.dao.weightsNow()
    val today = LocalDate.now()
    val t = today.toEpochDay()
    val trend = Trend.of(w.map { it.date to it.kg }, t)
    val now = if (w.size >= Trend.MIN_WEIGH_INS) trend.at(t) else w.lastOrNull()?.kg
    val goal = s.goalKg ?: return GoalData.NoGoal(now)
    val start = s.goalStartKg ?: now ?: return GoalData.NoGoal(null)
    val cur = now ?: start
    val dir = if (goal < start) -1.0 else 1.0
    val total = abs(start - goal).coerceAtLeast(0.1)
    val done = ((cur - start) * dir).coerceAtLeast(0.0)
    val left = ((goal - cur) * dir).coerceAtLeast(0.0)
    val status = if (w.size >= Trend.MIN_WEIGH_INS) Goal.status(trend, today, goal, s.weeklyRateKg, start) else null
    // Data del piano: partenza + (distanza / ritmo previsto).
    val planDate = s.goalStartDate?.let { LocalDate.ofEpochDay(it).plusDays(ceil(total / s.weeklyRateKg * 7).toLong()) }
    val eta = status?.eta
    val reachedOn = if (left <= 0.0) {
        val from = s.goalStartDate ?: trend.first
        (from..t).firstOrNull { d -> trend.at(d)?.let { (goal - it) * dir <= 0 } == true }?.let { LocalDate.ofEpochDay(it) } ?: today
    } else null
    return GoalData.Track(start, goal, (done / total).toFloat().coerceIn(0f, 1f), done, left, eta, eta != null && planDate != null && eta.isAfter(planDate), reachedOn)
}

class GoalWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = loadGoal(context)
        val disc = WidgetArt.disc()
        provideContent {
            val width = LocalSize.current.width
            Box(
                GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(WC.surface).padding(horizontal = 20.dp, vertical = 14.dp)
                    .clickable(open(context, MainActivity.OPEN_WEIGHT)),
                contentAlignment = Alignment.CenterStart,
            ) {
                when (d) {
                    is GoalData.NoGoal -> Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(GlanceModifier.defaultWeight()) {
                            Text("Nessun traguardo", style = TextStyle(color = WC.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                            Text(d.trend?.let { "Trend attuale ${Fmt.kg(it)} kg" } ?: "Nessuna pesata", style = TextStyle(color = WC.ink2, fontSize = 13.sp))
                        }
                        Row(
                            GlanceModifier.height(40.dp).cornerRadius(20.dp).background(WC.accentSoft).padding(horizontal = 14.dp)
                                .clickable(open(context, MainActivity.OPEN_GOAL)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(context, "flag", 16.dp, WC.accentInk, filled = false)
                            Spacer(GlanceModifier.width(6.dp))
                            Text("Imposta", style = TextStyle(color = WC.accentInk, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                    is GoalData.Track -> Column(GlanceModifier.fillMaxWidth()) {
                        val barW = (width - 40.dp - 36.dp - 64.dp).coerceAtLeast(60.dp)
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(Fmt.kg(d.start), style = TextStyle(color = WC.ink3, fontSize = 12.sp), modifier = GlanceModifier.width(36.dp))
                            Box(GlanceModifier.width(barW).height(18.dp), contentAlignment = Alignment.CenterStart) {
                                Box(GlanceModifier.width(barW).height(10.dp).cornerRadius(5.dp).background(WC.surface2)) {}
                                val fill = (barW - 18.dp) * d.progress + 9.dp
                                Box(GlanceModifier.width(fill).height(10.dp).cornerRadius(5.dp).background(WC.accent)) {}
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(GlanceModifier.width(fill - 9.dp))
                                    Box(GlanceModifier.size(18.dp), contentAlignment = Alignment.Center) {
                                        Tinted(disc, 18.dp, WC.surface)
                                        Tinted(disc, 12.dp, WC.accent)
                                    }
                                }
                            }
                            Spacer(GlanceModifier.defaultWeight())
                            Icon(context, "flag", 16.dp, WC.accentInk)
                            Spacer(GlanceModifier.width(3.dp))
                            Text(Fmt.kg(d.goal), style = TextStyle(color = WC.accentInk, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        }
                        Spacer(GlanceModifier.height(10.dp))
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (d.reachedOn != null) "Traguardo raggiunto" else "${Fmt.kg(d.done)} kg fatti · ${Fmt.kg(d.left)} da fare",
                                style = TextStyle(color = WC.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                modifier = GlanceModifier.defaultWeight(), maxLines = 1,
                            )
                            when {
                                d.reachedOn != null -> Text("il ${Fmt.dateShort(d.reachedOn)}", style = TextStyle(color = WC.ink2, fontSize = 13.sp))
                                d.eta == null -> Text("trend stabile", style = TextStyle(color = WC.ink2, fontSize = 13.sp))
                                d.late -> Text("in ritardo · ${Fmt.dateShort(d.eta)}", style = TextStyle(color = WC.accentInk, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                                else -> Text("arrivo ${Fmt.dateShort(d.eta)}", style = TextStyle(color = WC.ink2, fontSize = 13.sp))
                            }
                        }
                    }
                }
            }
        }
    }
}

class GoalWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = GoalWidget() }
