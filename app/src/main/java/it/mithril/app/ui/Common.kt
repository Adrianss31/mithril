package it.mithril.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.mithril.app.data.Repo
import it.mithril.app.data.SlotEntity
import it.mithril.app.data.VideoEntity
import it.mithril.app.domain.Sessions

@Composable
fun rememberRepo(): Repo {
    val context = LocalContext.current
    return remember(context) { Repo.get(context) }
}

@Composable
fun statusBarTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

@Composable
fun screenPadding(horizontal: Dp = 16.dp, top: Dp = 8.dp): PaddingValues =
    PaddingValues(start = horizontal, end = horizontal, top = statusBarTop() + top, bottom = BottomGap)

fun Navigator.startSlot(slot: SlotEntity, mini: Boolean, all: List<VideoEntity>) {
    val req = if (!mini) SessionRequest(slot.id, slot.videoIdList, false)
    else {
        val m = Sessions.miniFor(Sessions.videosOf(slot.videoIdList, all), all) ?: return startSlot(slot, false, all)
        SessionRequest(slot.id, listOf(m.id), true, "Versione mini")
    }
    push(Dest.Player(req))
}

fun Navigator.startVideos(ids: List<Long>, mini: Boolean = false, label: String = "Sessione libera") {
    if (ids.isNotEmpty()) push(Dest.Player(SessionRequest(null, ids, mini, label)))
}
