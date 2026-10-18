package it.mithril.app.ui.library

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mithril.app.ui.rememberRepo
import it.mithril.app.ui.statusBarTop
import it.mithril.app.ui.theme.pressScale
import it.mithril.app.ui.theme.rise
import it.mithril.app.ui.theme.ts
import kotlinx.coroutines.launch
import it.mithril.app.data.Tags
import it.mithril.app.data.VideoEntity
import it.mithril.app.data.YouTube
import it.mithril.app.domain.Fmt
import it.mithril.app.ui.LocalNav
import it.mithril.app.ui.SheetKind
import it.mithril.app.ui.components.Card
import it.mithril.app.ui.components.Chip
import it.mithril.app.ui.components.LocalToast
import it.mithril.app.ui.components.NumberBadge
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.components.RoundIconButton
import it.mithril.app.ui.components.Symbol
import it.mithril.app.ui.components.Tag
import it.mithril.app.ui.components.TextLink
import it.mithril.app.ui.components.Thumb
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T

/** Video di esempio (SELF, 30' HIIT, incorporabile): per provare l'app senza passare da YouTube. */
private const val SAMPLE_ID = "ml6cT4AZdqI"

@Composable
fun LibraryScreen() {
    val repo = rememberRepo()
    val nav = LocalNav.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val videosOrNull by repo.videos.collectAsStateWithLifecycle()
    val videos = videosOrNull ?: emptyList()
    var query by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable { mutableStateOf(setOf<String>()) }
    val shown = remember(videos, query, filters) { videos.filter { Tags.matches(it, filters, query) } }
    val epoch = nav.tabEpoch
    var adding by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = statusBarTop() + 14.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "head") {
                Column {
                    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
                        Text("Libreria", style = T.screenTitle, color = M.c.ink, modifier = Modifier.weight(1f))
                        if (videos.isNotEmpty()) Text(if (videos.size == 1) "1 video" else "${videos.size} video", style = T.secondary, color = M.c.ink2, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    if (videos.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        SearchField(query) { query = it }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Tags.FILTERS.forEach { f ->
                                Chip(f, f in filters, { filters = if (f in filters) filters - f else filters + f }, showCheck = true)
                            }
                        }
                    }
                }
            }
            if (videosOrNull != null && videos.isEmpty()) item(key = "empty") {
                Card(Modifier.rise(0, epoch).fillMaxWidth()) {
                    Text("I tuoi video, tutti qui", style = T.heroTitle, color = M.c.ink)
                    Spacer(Modifier.height(6.dp))
                    Text("Salva gli allenamenti di YouTube che ti piacciono: poi li metti in calendario e li avvii da Oggi.", style = T.body, color = M.c.ink2)
                    Spacer(Modifier.height(16.dp))
                    listOf("Apri un video nell'app YouTube", "Tocca Condividi", "Scegli Mithril: titolo e durata arrivano da soli").forEachIndexed { i, s ->
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            NumberBadge(i + 1, 24.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(s, style = T.body14, color = M.c.ink)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(if (adding) "Un attimo…" else "Prova con un video di esempio", {
                        if (!adding) {
                            adding = true
                            scope.launch {
                                val meta = runCatching { YouTube.fetch(SAMPLE_ID) }.getOrNull()
                                adding = false
                                if (meta == null) toast.show("Serve la connessione a internet") else nav.sheet = SheetKind.AddVideo(meta)
                            }
                        }
                    }, Modifier.fillMaxWidth(), icon = "auto_awesome", iconFilled = false, height = 48.dp)
                }
            }
            if (videos.isNotEmpty() && shown.isEmpty()) item(key = "none") {
                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nessun video con questi filtri.", style = T.body, color = M.c.ink2)
                    Spacer(Modifier.height(6.dp))
                    TextLink("Azzera filtri", { filters = emptySet(); query = "" })
                }
            }
            itemsIndexed(shown, key = { _, v -> v.id }) { i, v ->
                VideoCard(v, Modifier.animateItem().rise(i, epoch)) { nav.sheet = SheetKind.AddVideo(edit = v) }
            }
        }
        Fab(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp)) { nav.sheet = SheetKind.AddLink }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(M.c.surface2).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol("search", 22.dp, M.c.ink2)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Cerca titolo o canale", style = T.body, color = M.c.ink3)
            BasicTextField(value, onChange, singleLine = true, textStyle = T.body.copy(color = M.c.ink), cursorBrush = SolidColor(M.c.accent), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) Box(Modifier.clip(RoundedCornerShape(12.dp)).padding(2.dp)) {
            RoundIconButton("close", { onChange("") }, size = 28.dp, iconSize = 18.dp, bg = M.c.surface2, fg = M.c.ink2, contentDescription = "Cancella")
        }
    }
}

@Composable
fun VideoCard(v: VideoEntity, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.fillMaxWidth(), radius = 22.dp, padding = PaddingValues(12.dp), onClick = onClick) {
        Row {
            Thumb(v.thumbnailUrl, 116.dp, 66.dp, radius = 14.dp, duration = Fmt.clock(v.durationSec))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(v.title, style = T.title15, color = M.c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(v.channel, style = T.secondary, color = M.c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (v.tagList.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { v.tagList.take(3).forEach { Tag(it) } }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    when (v.timesDone) { 0 -> "mai fatto"; 1 -> "fatto 1 volta"; else -> "fatto ${v.timesDone} volte" } +
                        if (v.embeddable == false) " · si apre su YouTube" else "",
                    style = T.caption, color = M.c.ink3,
                )
            }
        }
    }
}

@Composable
private fun Fab(modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .pressScale(source)
            .then(if (M.c.dark) Modifier else Modifier.shadow(8.dp, shape, ambientColor = Color(0x553C281E), spotColor = Color(0x553C281E)))
            .height(56.dp)
            .clip(shape)
            .background(M.c.accent)
            .clickable(source, null, onClick = onClick)
            .padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol("add", 24.dp, M.c.onAccent)
        Spacer(Modifier.width(8.dp))
        Text("Aggiungi", style = ts(16, FontWeight.SemiBold), color = M.c.onAccent)
    }
}

/** Apre l'app YouTube (o il sito) per cercare un allenamento da condividere con Mithril. */
fun openYouTube(context: android.content.Context, query: String = "allenamento a casa") {
    val uri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun rememberOpenYouTube(): () -> Unit {
    val context = LocalContext.current
    return remember(context) { { openYouTube(context) } }
}
