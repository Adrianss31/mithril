package it.mithril.app.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import it.mithril.app.data.Repo
import it.mithril.app.data.VideoMeta
import it.mithril.app.data.YouTube
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.components.Sheet
import it.mithril.app.ui.components.Stripes
import it.mithril.app.ui.library.VideoForm
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.MithrilTheme
import it.mithril.app.ui.theme.T

/** Riceve un link da "Condividi" di YouTube e mostra il foglio "Aggiungi video" sopra YouTube. */
class ShareActivity : ComponentActivity() {
    private sealed interface St {
        data object Loading : St
        data class Loaded(val meta: VideoMeta) : St
        data class Error(val message: String) : St
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val text = intent?.takeIf { it.action == Intent.ACTION_SEND }?.let {
            listOfNotNull(it.getStringExtra(Intent.EXTRA_TEXT), it.getStringExtra(Intent.EXTRA_SUBJECT)).joinToString(" ")
        }
        val id = YouTube.parseId(text)
        val repo = Repo.get(this)
        setContent {
            MithrilTheme {
                val scope = rememberCoroutineScope()
                var state by remember { mutableStateOf<St?>(St.Loading) }
                var visible by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    state = if (id == null) St.Error("Questo non sembra un link di YouTube. Condividi il video dall'app YouTube.")
                    else runCatching { YouTube.fetch(id) }.fold({ St.Loaded(it) }, { St.Error("Serve la connessione a internet per leggere i dettagli del video.") })
                }
                fun close() {
                    visible = false
                    scope.launch { delay(320); finish(); @Suppress("DEPRECATION") overridePendingTransition(0, 0) }
                }
                Sheet(if (visible) state else null, onDismiss = { close() }) { st ->
                    when (st) {
                        St.Loading -> {
                            Text("Aggiungi video", style = T.sheetTitle, color = M.c.ink)
                            Spacer(Modifier.height(14.dp))
                            Stripes(Modifier.fillMaxWidth().height(150.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Recupero titolo, canale e durata…", style = T.body14, color = M.c.ink2)
                            Spacer(Modifier.height(80.dp))
                        }
                        is St.Error -> {
                            Text("Aggiungi video", style = T.sheetTitle, color = M.c.ink)
                            Spacer(Modifier.height(10.dp))
                            Text(st.message, style = T.body, color = M.c.ink2)
                            Spacer(Modifier.height(18.dp))
                            PrimaryButton("Chiudi", { close() }, Modifier.fillMaxWidth())
                        }
                        is St.Loaded -> VideoForm(st.meta, null, onSave = { title, tags, mini ->
                            scope.launch {
                                repo.addVideo(st.meta.copy(title = title), tags, mini)
                                Toast.makeText(applicationContext, "Salvato in Libreria", Toast.LENGTH_SHORT).show()
                                close()
                            }
                        })
                    }
                }
            }
        }
    }
}
