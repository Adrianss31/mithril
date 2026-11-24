package it.mithril.app.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Server HTTP minimo che offre UN file del telefono alla TV durante la trasmissione.
 * Risponde solo al percorso con il token casuale e supporta le richieste Range (servono per i salti).
 */
class LocalMediaServer(private val context: Context, private val uri: Uri, private val mime: String) {
    private val token = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val boundIp = localIpv4(context)
    private val server = ServerSocket().apply {
        reuseAddress = true
        val addr = boundIp?.let { runCatching { InetSocketAddress(InetAddress.getByName(it), 0) }.getOrNull() } ?: InetSocketAddress(0)
        runCatching { bind(addr) }.onFailure { bind(InetSocketAddress(0)) }
    }
    private val pool = Executors.newFixedThreadPool(4) { r -> Thread(r, "mithril-cast-worker").apply { isDaemon = true } }
    @Volatile private var running = true

    init {
        thread(name = "mithril-cast-server", isDaemon = true) {
            while (running) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    pool.execute { runCatching { serve(socket) }; runCatching { socket.close() } }
                }.onFailure { runCatching { socket.close() } }
            }
        }
    }

    /** URL raggiungibile dalla TV sulla stessa rete Wi-Fi, o null se il telefono non ha un IPv4 locale. */
    fun url(): String? {
        val ip = boundIp ?: localIpv4(context) ?: return null
        return "http://$ip:${server.localPort}/$token/video"
    }

    fun stop() {
        running = false
        runCatching { server.close() }
        runCatching { pool.shutdownNow() }
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = 30_000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
        val request = reader.readLine() ?: return
        var rangeHeader: String? = null
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Range:", ignoreCase = true)) rangeHeader = line.substringAfter(':').trim()
        }
        val parts = request.split(' ')
        val out = socket.getOutputStream()
        if (parts.size < 2 || !parts[1].startsWith("/$token/")) return respond(out, "404 Not Found", emptyMap())
        val head = parts[0] == "HEAD"
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { input ->
                val size = if (pfd.statSize > 0) pfd.statSize else runCatching { input.channel.size() }.getOrDefault(-1L)
                if (size <= 0L) {
                    if (rangeHeader != null) return respond(out, "416 Range Not Satisfiable", mapOf("Content-Range" to "bytes */*"))
                    respond(out, "200 OK", mapOf("Content-Type" to mime, "Access-Control-Allow-Origin" to "*"))
                    if (head) return
                    val buf = ByteArray(256 * 1024)
                    while (running) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                    out.flush()
                    return
                }
                val parsed = parseByteRange(rangeHeader, size)
                if (parsed == null) return respond(out, "416 Range Not Satisfiable", mapOf("Content-Range" to "bytes */$size"))
                val start = parsed.first
                val end = parsed.last
                val length = end - start + 1
                val headers = mutableMapOf(
                    "Content-Type" to mime,
                    "Accept-Ranges" to "bytes",
                    "Content-Length" to length.toString(),
                    "Access-Control-Allow-Origin" to "*",
                )
                val isPartial = rangeHeader != null
                if (isPartial) headers["Content-Range"] = "bytes $start-$end/$size"
                respond(out, if (isPartial) "206 Partial Content" else "200 OK", headers)
                if (head) return
                input.channel.position(start)
                val buf = ByteArray(256 * 1024)
                var left = length
                while (left > 0 && running) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) break
                    out.write(buf, 0, n); left -= n
                }
                out.flush()
            }
        } ?: respond(out, "404 Not Found", emptyMap())
    }

    private fun respond(out: OutputStream, status: String, headers: Map<String, String>) {
        val sb = StringBuilder("HTTP/1.1 $status\r\nConnection: close\r\n")
        headers.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        if ("Content-Length" !in headers && "200" !in status) sb.append("Content-Length: 0\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1)); out.flush()
    }

    companion object {
        internal fun parseByteRange(header: String?, size: Long): LongRange? {
            if (size <= 0L) return null
            if (header == null) return 0L until size
            val raw = header.trim()
            if (!raw.startsWith("bytes=", ignoreCase = true)) return null
            val spec = raw.substring(6).trim()
            if (spec.isEmpty() || spec.contains(',')) return null
            val dash = spec.indexOf('-')
            if (dash < 0) return null
            val a = spec.substring(0, dash).trim()
            val b = spec.substring(dash + 1).trim()
            val start: Long
            val end: Long
            if (a.isEmpty()) {
                val suffix = b.toLongOrNull()?.takeIf { it > 0L } ?: return null
                start = (size - suffix).coerceAtLeast(0L)
                end = size - 1L
            } else {
                start = a.toLongOrNull()?.takeIf { it >= 0L } ?: return null
                if (start >= size) return null
                end = if (b.isEmpty()) size - 1L else minOf(b.toLongOrNull()?.takeIf { it >= start } ?: return null, size - 1L)
            }
            return if (start <= end) start..end else null
        }

        @Suppress("DEPRECATION")
        fun localIpv4(context: Context): String? {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val wifiNet = cm.allNetworks.firstOrNull { net ->
                val caps = cm.getNetworkCapabilities(net) ?: return@firstOrNull false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
            val net = wifiNet ?: cm.activeNetwork?.takeIf { n ->
                cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) != true
            } ?: return null
            val props = cm.getLinkProperties(net) ?: return null
            return props.linkAddresses.map { it.address }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress
        }
    }
}
