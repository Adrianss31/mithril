package it.mithril.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
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
import coil.ImageLoader
import coil.request.ImageRequest
import java.time.LocalDate
import java.time.LocalTime
import it.mithril.app.MainActivity
import it.mithril.app.R
import it.mithril.app.data.Repo
import it.mithril.app.data.SlotEntity
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Goal
import it.mithril.app.domain.Sessions
import it.mithril.app.domain.Trend
import it.mithril.app.ui.theme.DarkColors
import it.mithril.app.ui.theme.LightColors

private object WC {
    val surface = ColorProvider(LightColors.surface, DarkColors.surface)
    val surface2 = ColorProvider(LightColors.surface2, DarkColors.surface2)
    val ink = ColorProvider(LightColors.ink, DarkColors.ink)
    val ink2 = ColorProvider(LightColors.ink2, DarkColors.ink2)
    val ink3 = ColorProvider(LightColors.ink3, DarkColors.ink3)
    val accent = ColorProvider(LightColors.accent, DarkColors.accent)
    val onAccent = ColorProvider(LightColors.onAccent, DarkColors.onAccent)
}

object Widgets {
    suspend fun updateAll(context: Context) {
        runCatching { WeightWidget().updateAll(context) }
        runCatching { SessionWidget().updateAll(context) }
    }
}

// ---------- Peso ----------

private data class WeightData(
    val value: Double?,
    val isTrend: Boolean,
    val weekChange: Double?,
    val goalKg: Double?,
    val progress: Float?,
    val onTrack: Boolean?,
    val remaining: Double?,
    val eta: LocalDate?,
)

private suspend fun loadWeight(context: Context): WeightData {
    val repo = Repo.get(context)
    val w = repo.dao.weightsNow()
    val s = repo.settings.now()
    val today = LocalDate.now()
    val t = today.toEpochDay()
    if (w.size < Trend.MIN_WEIGH_INS) return WeightData(w.lastOrNull()?.kg, false, null, s.goalKg, null, null, null, null)
    val trend = Trend.of(w.map { it.date to it.kg }, t)
    val now = trend.at(t)
    val status = s.goalKg?.let { Goal.status(trend, today, it, s.weeklyRateKg, s.goalStartKg) }
    return WeightData(now, true, trend.change(t, 7), s.goalKg, status?.progress, status?.onTrack, s.goalKg?.let { g -> now?.let { g - it } }, status?.eta)
}

class WeightWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = loadWeight(context)
        val open = Intent(context, MainActivity::class.java).setAction(MainActivity.OPEN_WEIGHT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val quick = Intent(context, QuickWeightActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(WC.surface)
                    .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)
                    .clickable(actionStartActivity(open)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(ImageProvider(R.drawable.ic_app_round), null, GlanceModifier.size(16.dp))
                            Spacer(GlanceModifier.width(6.dp))
                            Text(if (d.isTrend) "Peso · trend" else "Peso", style = TextStyle(color = WC.ink2, fontSize = 12.sp))
                        }
                        Spacer(GlanceModifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(d.value?.let { Fmt.kg(it) } ?: "—", style = TextStyle(color = WC.ink, fontSize = 28.sp, fontWeight = FontWeight.Medium))
                            Text(" kg", style = TextStyle(color = WC.ink2, fontSize = 13.sp), modifier = GlanceModifier.padding(bottom = 4.dp))
                            if (d.weekChange != null) {
                                Text("  ${Fmt.signedKg(d.weekChange)} kg", style = TextStyle(color = WC.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.padding(bottom = 4.dp))
                                Text(" vs sett. scorsa", style = TextStyle(color = WC.ink3, fontSize = 12.sp), modifier = GlanceModifier.padding(bottom = 4.dp))
                            }
                        }
                    }
                    Box(
                        GlanceModifier.size(44.dp).cornerRadius(22.dp).background(WC.accent).clickable(actionStartActivity(quick)),
                        contentAlignment = Alignment.Center,
                    ) { Text("+", style = TextStyle(color = WC.onAccent, fontSize = 24.sp, fontWeight = FontWeight.Medium)) }
                }
                if (d.goalKg != null && d.progress != null) {
                    Spacer(GlanceModifier.height(10.dp))
                    LinearProgressIndicator(d.progress, GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp), color = WC.accent, backgroundColor = WC.surface2)
                    Spacer(GlanceModifier.height(8.dp))
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("● ", style = TextStyle(color = WC.accent, fontSize = 10.sp))
                        Text(
                            if (d.onTrack == true) "In linea col piano" else "Sotto il ritmo previsto",
                            style = TextStyle(color = WC.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Text(
                            buildString {
                                d.remaining?.let { append("${Fmt.signedKg(it)} kg a ${Fmt.kg(d.goalKg)}") }
                                d.eta?.let { append(" · ${Fmt.eta(it)}") }
                            },
                            style = TextStyle(color = WC.ink2, fontSize = 12.sp),
                        )
                    }
                } else if (!d.isTrend) {
                    Spacer(GlanceModifier.height(8.dp))
                    Text("Con 3 pesate compare il trend", style = TextStyle(color = WC.ink3, fontSize = 12.sp))
                }
            }
        }
    }
}

class WeightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeightWidget()
}

// ---------- Sessione ----------

private data class SessionData(val slot: SlotEntity?, val title: String, val line: String, val thumb: Bitmap?)

private suspend fun loadSession(context: Context): SessionData {
    val repo = Repo.get(context)
    val today = LocalDate.now()
    val logs = repo.dao.logsFrom(today.toEpochDay() - 1)
    val slots = repo.dao.slotsFrom(today.toEpochDay())
    val now = LocalTime.now().let { it.hour * 60 + it.minute }
    val slot = Sessions.next(slots, logs, today, now) ?: return SessionData(null, "Niente in programma", "Pianifica i prossimi giorni", null)
    val all = repo.dao.videosNow()
    val videos = Sessions.videosOf(slot.videoIdList, all)
    val day = LocalDate.ofEpochDay(slot.date)
    val prefix = when (day) {
        today -> ""
        today.plusDays(1) -> "domani "
        else -> Fmt.dayShortName(day.dayOfWeek).lowercase() + " "
    }
    val thumb = videos.firstOrNull()?.thumbnailUrl?.let { url ->
        runCatching {
            val req = ImageRequest.Builder(context).data(url).size(216, 144).allowHardware(false).build()
            (ImageLoader(context).execute(req).drawable as? BitmapDrawable)?.bitmap
        }.getOrNull()
    }
    return SessionData(slot, Sessions.title(videos, short = true), "$prefix${Fmt.time(slot.timeMin)} · ${slot.place} · ${Fmt.minutes(Sessions.totalSec(videos))}", thumb)
}

class SessionWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = loadSession(context)
        val open = Intent(context, MainActivity::class.java).setAction(if (d.slot == null) MainActivity.OPEN_PLANNER else MainActivity.OPEN_TODAY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val start = d.slot?.let {
            Intent(context, MainActivity::class.java).setAction(MainActivity.START).putExtra(MainActivity.EXTRA_SLOT, it.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        provideContent {
            Row(
                GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(WC.surface).padding(12.dp).clickable(actionStartActivity(open)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (d.thumb != null) Image(ImageProvider(d.thumb), null, GlanceModifier.size(72.dp, 48.dp).cornerRadius(14.dp), contentScale = ContentScale.Crop)
                else Box(GlanceModifier.size(72.dp, 48.dp).cornerRadius(14.dp).background(WC.surface2)) {}
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(d.title, style = TextStyle(color = WC.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(d.line, style = TextStyle(color = WC.ink2, fontSize = 12.sp), maxLines = 1)
                }
                Spacer(GlanceModifier.width(8.dp))
                Box(
                    GlanceModifier.height(44.dp).cornerRadius(22.dp).background(WC.accent).padding(horizontal = 16.dp)
                        .clickable(actionStartActivity(start ?: open)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (start != null) "▶  Avvia" else "Pianifica", style = TextStyle(color = WC.onAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

class SessionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SessionWidget()
}

