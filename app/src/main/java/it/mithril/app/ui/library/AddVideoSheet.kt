package it.mithril.app.ui.library

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.input.KeyboardCapitalization
import it.mithril.app.data.LocalVideos
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mithril.app.ui.rememberRepo
import it.mithril.app.ui.startVideos
import it.mithril.app.ui.theme.ts
import kotlinx.coroutines.launch
import it.mithril.app.data.Tags
import it.mithril.app.data.VideoEntity
import it.mithril.app.data.VideoMeta
import it.mithril.app.data.YouTube
import it.mithril.app.domain.Fmt
import it.mithril.app.domain.Sessions
import it.mithril.app.ui.LocalNav
import it.mithril.app.ui.SheetKind
import it.mithril.app.ui.components.Chip
import it.mithril.app.ui.components.LocalToast
import it.mithril.app.ui.components.OutlineButton
import it.mithril.app.ui.components.Overline
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.components.RoundIconButton
import it.mithril.app.ui.components.Symbol
import it.mithril.app.ui.components.Thumb
import it.mithril.app.ui.components.TonalButton
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T

/** Foglio "Aggiungi video" (nuovo da YouTube) o "Modifica video" (dalla Libreria). */
@Composable
fun ColumnScope.AddVideoSheet(s: SheetKind.AddVideo, onDone: () -> Unit) {
    val nav = LocalNav.current
    val repo = rememberRepo()
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val edit = s.edit
    val meta = s.meta ?: edit?.let { VideoMeta(it.youtubeId, it.title, it.channel, it.durationSec, it.thumbnailUrl, it.embeddable) } ?: return
    VideoForm(
        meta = meta,
        edit = edit,
        onSave = { title, tags, mini ->
            scope.launch {
                if (edit != null) repo.updateVideo(edit.copy(title = title, tags = Tags.join(tags), miniVideoId = mini))
                else repo.addVideo(meta.copy(title = title), tags, mini)
                toast.show(if (edit != null) "Video aggiornato" else "Salvato in Libreria")
            }
            onDone()
        },
        onDelete = edit?.let { v ->
            {
                scope.launch {
                    repo.deleteVideo(v.id)
                    toast.show("Video eliminato")
                }
                onDone()
            }
        },
        onPlay = edit?.let { v -> { onDone(); nav.startVideos(listOf(v.id)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.VideoForm(
    meta: VideoMeta,
    edit: VideoEntity?,
    onSave: (title: String, tags: Set<String>, mini: Long?) -> Unit,
    onDelete: (() -> Unit)? = null,
    onPlay: (() -> Unit)? = null,
) {
    val repo = rememberRepo()
    val videos by repo.videos.collectAsStateWithLifecycle()
    var title by remember(meta.youtubeId) { mutableStateOf(edit?.title ?: meta.title) }
    val local = meta.localUri != null
    var tags by remember(meta.youtubeId) { mutableStateOf(edit?.tagList?.toSet() ?: Tags.suggest(meta.title, meta.durationSec)) }
    var mini by remember(meta.youtubeId) { mutableStateOf(edit?.miniVideoId) }
    val minis = (videos ?: emptyList()).filter { it.youtubeId != meta.youtubeId && (it.durationSec ?: Int.MAX_VALUE) <= Sessions.MINI_MAX_SEC }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (edit != null) "Modifica video" else "Aggiungi video", style = T.sheetTitle, color = M.c.ink, modifier = Modifier.weight(1f))
        if (onDelete != null) RoundIconButton("delete", onDelete, size = 36.dp, iconSize = 20.dp, contentDescription = "Elimina video")
        else {
            Symbol(if (local) "smartphone" else "share", 18.dp, M.c.ink2)
            Spacer(Modifier.width(4.dp))
            Text(if (local) "dal telefono" else "da YouTube", style = T.secondary, color = M.c.ink2)
        }
    }
    Spacer(Modifier.height(14.dp))
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(M.c.surface2).padding(12.dp)) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                Thumb(meta.thumbnailUrl, maxWidth, 150.dp, radius = 14.dp, duration = Fmt.clock(meta.durationSec))
            }
            Spacer(Modifier.height(10.dp))
            TitleField(title) { title = it }
            Text(
                meta.channel + (meta.durationSec?.let { " · ${(it + 30) / 60} min" } ?: ""),
                style = T.secondary, color = M.c.ink2, modifier = Modifier.padding(horizontal = 4.dp),
            )
            if (edit == null) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Symbol("auto_awesome", 14.dp, M.c.ink3)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when {
                            local -> "Dalla memoria del telefono: si guarda anche senza internet"
                            meta.durationSec != null -> "Titolo, canale e durata recuperati in automatico"
                            else -> "Durata: la leggo al primo avvio"
                        },
                        style = T.caption, color = M.c.ink3,
                    )
                }
            }
            if (meta.embeddable == false) {
                Spacer(Modifier.height(6.dp))
                Text("Il canale non permette la riproduzione dentro Mithril: si aprirà su YouTube.", style = T.caption, color = M.c.accentInk, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Overline("Etichette")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tags.EDITABLE.forEach { t -> Chip(t, t in tags, { tags = if (t in tags) tags - t else tags + t }, showCheck = true) }
        }
        Spacer(Modifier.height(18.dp))
        Overline("Versione mini (facoltativa)")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Nessuna", mini == null, { mini = null })
            minis.forEach { v -> Chip(v.title.let { t -> if (t.length > 28) t.take(26).trimEnd() + "…" else t }, mini == v.id, { mini = v.id }) }
        }
        if (minis.isEmpty()) Text("Quando avrai video da 10 minuti o meno, potrai sceglierne uno come versione mini.", style = T.caption, color = M.c.ink3, modifier = Modifier.padding(top = 8.dp))
    }
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onPlay != null) TonalButton("Avvia", onPlay, icon = "play_arrow")
        PrimaryButton(if (edit != null) "Salva" else "Salva in Libreria", { onSave(title.trim().ifBlank { meta.title }, tags, mini) }, Modifier.weight(1f))
    }
}

/** Dal "+ Aggiungi" della Libreria: incolla un link oppure cerca su YouTube e condividi. */
@Composable
fun ColumnScope.AddLinkSheet(onDone: (SheetKind?) -> Unit) {
    val context = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(clipboardLink(context) ?: "") }
    var loading by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                val meta = runCatching { LocalVideos.import(context, uri) }.getOrNull()
                importing = false
                if (meta == null) toast.show("Non riesco a leggere questo file") else onDone(SheetKind.AddVideo(meta))
            }
        }
    }
    val id = YouTube.parseId(text)
    fun go() {
        val vid = id ?: return toast.show("Non trovo un link YouTube valido")
        loading = true
        scope.launch {
            val meta = runCatching { YouTube.fetch(vid) }.getOrNull()
            loading = false
            if (meta == null) toast.show("Serve la connessione a internet") else onDone(SheetKind.AddVideo(meta))
        }
    }
    Text("Aggiungi video", style = T.sheetTitle, color = M.c.ink)
    Spacer(Modifier.height(6.dp))
    Text("Il modo più rapido: dall'app YouTube tocca Condividi e scegli Mithril. Oppure incolla qui il link.", style = T.body14, color = M.c.ink2)
    Spacer(Modifier.height(16.dp))
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(M.c.surface2).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (text.isEmpty()) Text("https://youtu.be/…", style = T.body, color = M.c.ink3)
        BasicTextField(
            text, { text = it }, singleLine = true,
            textStyle = T.body.copy(color = M.c.ink), cursorBrush = SolidColor(M.c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { go() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(16.dp))
    PrimaryButton(if (loading) "Recupero i dettagli…" else "Continua", { if (!loading) go() }, Modifier.fillMaxWidth(), enabled = id != null)
    Spacer(Modifier.height(10.dp))
    OutlineButton("Cerca su YouTube", { openYouTube(context); onDone(null) }, Modifier.fillMaxWidth(), icon = "open_in_new")
    Spacer(Modifier.height(20.dp))
    Overline("Oppure dal telefono")
    Spacer(Modifier.height(6.dp))
    Text("Un video che hai già in memoria (un corso comprato, un tuo video): si guarda senza internet e si può trasmettere alla TV.", style = T.body14, color = M.c.ink2)
    Spacer(Modifier.height(12.dp))
    TonalButton(if (importing) "Leggo il file…" else "Scegli un file dal telefono", { if (!importing) picker.launch(arrayOf("video/*")) }, Modifier.fillMaxWidth(), icon = "folder_open")
}

@Composable
private fun TitleField(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(M.c.surface).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value, { onChange(it.take(120)) },
            textStyle = ts(17, FontWeight.SemiBold).copy(color = M.c.ink),
            cursorBrush = SolidColor(M.c.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            maxLines = 3,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Symbol("edit", 18.dp, M.c.ink3)
    }
}

private fun clipboardLink(context: Context): String? = runCatching {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { YouTube.parseId(it) != null }
}.getOrNull()
