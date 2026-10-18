package it.mithril.app.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import it.mithril.app.data.SlotEntity
import it.mithril.app.data.VideoEntity
import it.mithril.app.data.VideoMeta

enum class Tab(val label: String, val icon: String) {
    TODAY("Oggi", "wb_twilight"),
    PLANNER("Planner", "calendar_view_week"),
    LIBRARY("Libreria", "video_library"),
    WEIGHT("Peso", "monitor_weight"),
}

/** Cosa riprodurre: una sequenza ordinata di video, legata (o no) a uno slot del planner. */
data class SessionRequest(
    val slotId: Long?,
    val videoIds: List<Long>,
    val mini: Boolean,
    val label: String = "Sessione di stasera",
)

sealed interface Dest {
    data class Player(val req: SessionRequest) : Dest
    data class External(val req: SessionRequest, val videoId: Long) : Dest
    data class Completion(val logId: Long, val req: SessionRequest, val videosDone: Int) : Dest
    data class Review(val weekStart: LocalDate) : Dest
    data class PlanWeek(val weekStart: LocalDate) : Dest
    data object Settings : Dest
    data object AllWeights : Dest
}

sealed interface SheetKind {
    data class AddVideo(val meta: VideoMeta? = null, val edit: VideoEntity? = null) : SheetKind
    data class NewSlot(val date: LocalDate, val edit: SlotEntity? = null, val onlyWhenWhere: Boolean = false, val onPicked: ((Int, String) -> Unit)? = null) : SheetKind
    data class Weigh(val editDate: LocalDate? = null) : SheetKind
    data object AddLink : SheetKind
}

@Stable
class Navigator {
    var tab by mutableStateOf(Tab.TODAY)
    val stack = mutableStateListOf<Dest>()
    var sheet by mutableStateOf<SheetKind?>(null)
    /** Incrementato a ogni cambio di tab per rigiocare le animazioni di ingresso. */
    var tabEpoch by mutableStateOf(0)
        private set

    val top: Dest? get() = stack.lastOrNull()

    fun select(t: Tab) { if (t != tab) { tab = t; tabEpoch++ } }
    fun push(d: Dest) { stack.add(d) }
    fun replace(d: Dest) { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex); stack.add(d) }
    fun pop(): Boolean = if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); true } else false
    fun popToRoot() { stack.clear() }
}
