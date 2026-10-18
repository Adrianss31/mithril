package it.mithril.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class VideoMeta(
    val youtubeId: String,
    val title: String,
    val channel: String,
    val durationSec: Int?,
    val thumbnailUrl: String,
    val embeddable: Boolean?,
    val localUri: String? = null,
)

/** Metadati YouTube senza chiave API: oEmbed per titolo/canale, pagina pubblica per la durata. */
object YouTube {
    private val ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val PATTERNS = listOf(
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
        Regex("""[?&]v=([A-Za-z0-9_-]{11})"""),
        Regex("""youtube\.com/(?:shorts|embed|live|v)/([A-Za-z0-9_-]{11})"""),
    )

    /** Estrae l'id da un testo condiviso (può contenere titolo + link). */
    fun parseId(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val t = text.trim()
        if (ID.matches(t)) return t
        return PATTERNS.firstNotNullOfOrNull { it.find(t)?.groupValues?.get(1) }
    }

    fun thumbnail(id: String) = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    suspend fun fetch(id: String): VideoMeta = withContext(Dispatchers.IO) {
        var title: String? = null
        var channel: String? = null
        var embeddable: Boolean? = null
        runCatching {
            val url = "https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(watchUrl(id), "UTF-8")
            val (code, body) = get(url)
            when (code) {
                200 -> JSONObject(body).let { title = it.optString("title"); channel = it.optString("author_name") }
                401, 403 -> embeddable = false
            }
        }
        var duration: Int? = null
        runCatching {
            val (code, page) = get(watchUrl(id) + "&hl=it&gl=IT")
            if (code == 200) {
                duration = Regex("\"lengthSeconds\":\"(\\d+)\"").find(page)?.groupValues?.get(1)?.toIntOrNull()
                Regex("\"playableInEmbed\":(true|false)").find(page)?.groupValues?.get(1)?.let { embeddable = it == "true" }
                if (title.isNullOrBlank()) title = Regex("<meta name=\"title\" content=\"([^\"]*)\"").find(page)?.groupValues?.get(1)?.let(::unescape)
                if (channel.isNullOrBlank()) channel = Regex("\"ownerChannelName\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(page)?.groupValues?.get(1)?.let(::unescapeJson)
            }
        }
        VideoMeta(
            youtubeId = id,
            title = title?.takeIf { it.isNotBlank() } ?: "Video YouTube",
            channel = channel?.takeIf { it.isNotBlank() } ?: "YouTube",
            durationSec = duration,
            thumbnailUrl = thumbnail(id),
            embeddable = embeddable,
        )
    }

    private fun get(url: String): Pair<Int, String> {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36")
            setRequestProperty("Accept-Language", "it-IT,it;q=0.9")
            // Evita la pagina di consenso cookie europea.
            setRequestProperty("Cookie", "SOCS=CAI; CONSENT=YES+cb")
        }
        return try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.use { input ->
                input.readBytes().let { if (it.size > 3_000_000) it.copyOf(3_000_000) else it }.toString(Charsets.UTF_8)
            } ?: ""
            code to body
        } finally { c.disconnect() }
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
    private fun unescapeJson(s: String) = runCatching { JSONObject("{\"v\":\"$s\"}").getString("v") }.getOrDefault(s)
}
