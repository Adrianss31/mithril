package it.mithril.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mithril.app.cast.Cast
import it.mithril.app.ui.components.OutlineButton
import it.mithril.app.ui.components.Symbol
import it.mithril.app.ui.theme.M
import it.mithril.app.ui.theme.T

/** Scelta della TV (Google TV, Chromecast) per trasmettere il video del telefono in riproduzione. */
@Composable
fun ColumnScope.CastSheet(onDone: () -> Unit) {
    val ui by Cast.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        Cast.startDiscovery()
        onDispose { Cast.stopDiscovery() }
    }
    // Aperto per collegarsi: appena la TV risponde il foglio si chiude da solo.
    val startedConnected = remember { ui.connectedTo != null }
    LaunchedEffect(ui.connectedTo) { if (!startedConnected && ui.connectedTo != null) onDone() }

    Text("Trasmetti alla TV", style = T.sheetTitle, color = M.c.ink)
    Spacer(Modifier.height(6.dp))
    if (ui.connectedTo != null) {
        Text("Il video va su ${ui.connectedTo}. I comandi del telefono controllano la TV e la sessione conta come sempre.", style = T.body14, color = M.c.ink2)
        Spacer(Modifier.height(18.dp))
        OutlineButton("Interrompi trasmissione", { Cast.disconnect(); onDone() }, Modifier.fillMaxWidth(), icon = "close")
        return
    }
    Text(
        if (ui.connecting) "Mi collego…" else "Telefono e TV devono essere sulla stessa rete Wi-Fi.",
        style = T.body14, color = M.c.ink2,
    )
    Spacer(Modifier.height(14.dp))
    if (ui.devices.isEmpty()) {
        Text("Cerco dispositivi sulla rete…", style = T.secondary, color = M.c.ink3, modifier = Modifier.padding(vertical = 18.dp))
    }
    ui.devices.forEach { d ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(enabled = !ui.connecting) { Cast.connect(d.id) }.padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(M.c.surface2), contentAlignment = Alignment.Center) { Symbol("tv", 22.dp, M.c.ink) }
            Spacer(Modifier.width(14.dp))
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(d.name, style = T.title15, color = M.c.ink)
                d.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = T.secondary, color = M.c.ink2) }
            }
            Symbol("cast", 22.dp, M.c.ink2)
        }
    }
    Spacer(Modifier.height(12.dp))
}
