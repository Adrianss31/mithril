package it.mithril.app.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import it.mithril.app.ui.components.cardShadow
import kotlinx.coroutines.launch
import java.time.LocalDate
import it.mithril.app.data.Repo
import it.mithril.app.domain.Fmt
import it.mithril.app.ui.components.PrimaryButton
import it.mithril.app.ui.components.TextLink
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.MithrilTheme
import it.mithril.app.ui.theme.T
import it.mithril.app.ui.weight.WeightWheel

/** Popup "Pesata di oggi" che si apre dal + del widget, sopra la home. */
class QuickWeightActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repo = Repo.get(this)
        setContent {
            MithrilTheme {
                val scope = rememberCoroutineScope()
                var tenths by remember { mutableIntStateOf(0) }
                var loaded by remember { mutableStateOf(false) }
                var closing by remember { mutableStateOf(false) }
                val motion = M.motion
                val scrim = remember { Animatable(if (motion) 0f else 1f) }
                val card = remember { Animatable(if (motion) 0f else 1f) }
                LaunchedEffect(Unit) {
                    val w = repo.dao.weightsNow()
                    val today = w.firstOrNull { it.date == LocalDate.now().toEpochDay() } ?: w.lastOrNull()
                    tenths = Math.round((today?.kg ?: 75.0) * 10).toInt()
                    loaded = true
                    if (motion) {
                        launch { scrim.animateTo(1f, tween(250)) }
                        card.animateTo(1f, spring(0.6f, 400f))
                    }
                }
                fun close() {
                    if (closing) return
                    closing = true
                    scope.launch {
                        if (motion) { launch { card.animateTo(0f, tween(180)) }; scrim.animateTo(0f, tween(200)) }
                        finish()
                        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
                    }
                }
                BackHandler { close() }
                Box(
                    Modifier.fillMaxSize().graphicsLayer { alpha = scrim.value }.background(M.c.scrim)
                        .clickable(remember { MutableInteractionSource() }, null) { close() },
                    contentAlignment = Alignment.Center,
                ) {
                    val shape = RoundedCornerShape(28.dp)
                    Column(
                        Modifier
                            .padding(24.dp)
                            .widthIn(max = 300.dp)
                            .graphicsLayer {
                                val v = card.value
                                alpha = v.coerceIn(0f, 1f); scaleX = 0.88f + 0.12f * v; scaleY = 0.88f + 0.12f * v
                                translationY = (1f - v) * 10.dp.toPx()
                            }
                            .cardShadow(shape)
                            .clip(shape)
                            .background(M.c.surface)
                            .clickable(remember { MutableInteractionSource() }, null) {}
                            .padding(20.dp),
                    ) {
                        Text("Pesata di oggi", style = T.sheetTitle, color = M.c.ink)
                        Spacer(Modifier.height(18.dp))
                        if (loaded) WeightWheel(tenths, { tenths = it.coerceIn(200, 3000) }, stepper = 48.dp, big = false)
                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            TextLink("Annulla", { close() }, color = M.c.ink2)
                            Spacer(Modifier.height(0.dp).padding(horizontal = 6.dp))
                            PrimaryButton("Salva", {
                                val kg = tenths / 10.0
                                lifecycleScope.launch {
                                    repo.saveWeight(LocalDate.now(), kg)
                                    Toast.makeText(applicationContext, "Pesata salvata · ${Fmt.kg(kg)} kg", Toast.LENGTH_SHORT).show()
                                    close()
                                }
                            }, height = 48.dp, enabled = loaded)
                        }
                    }
                }
            }
        }
    }
}
